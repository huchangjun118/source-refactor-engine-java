package com.github.huchangjun118.sourcerefactorenginejava.refactor.engine;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.cache.CacheStore;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.commit.FileCommitter;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.ConflictStrategy;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.ParseErrorPolicy;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.ChangeEntry;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.ChangeManifest;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.EditConflict;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.SkippedFile;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.TextEdit;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.parse.ParseOutcome;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.parse.ParsedFile;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.parse.SourceParser;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.rule.RewriteRule;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.rule.RuleContext;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.rule.RuleFactory;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Orchestrates a refactor task.
 *
 * <p>Pipeline: scan -&gt; for each rule (in deterministic order): ensure
 * declarations (cached per content hash), collect edits (cached per
 * content/declarations/rule fingerprint), detect conflicts, apply edits to the
 * in-memory state, re-parse changed files. Files are parsed lazily: a file is
 * only (re-)parsed when the cache cannot answer for its current content, so
 * incremental runs only recompute what the input change actually affects.
 * Preview and apply share the exact same computation; nothing is written to
 * the working tree before the final atomic commit.
 */
public class RefactorEngine {

    private final RefactorConfig config;
    private final FileCommitter committer;
    private final List<RewriteRule> rules;
    private final List<String> configWarnings = new ArrayList<>();

    public RefactorEngine(RefactorConfig config) {
        this(config, new FileCommitter());
    }

    public RefactorEngine(RefactorConfig config, FileCommitter committer) {
        this.config = config;
        this.committer = committer;
        this.rules = RuleFactory.createAll(config.getRules(), configWarnings);
    }

    /** Computes the full change set without touching the working tree. */
    public TaskResult preview() {
        return run(false);
    }

    /** Computes the change set and commits it atomically (all files or none). */
    public TaskResult apply() {
        return run(true);
    }

    /** Restores the working tree to the state before the given task was committed. */
    public void rollback(String taskId) {
        committer.rollback(config.getSourceDir(), config.journalDir(), taskId);
    }

    private TaskResult run(boolean doCommit) {
        long deadline = config.getTimeoutMillis() > 0
                ? System.currentTimeMillis() + config.getTimeoutMillis()
                : Long.MAX_VALUE;
        Path root = config.getSourceDir();
        CacheStore cache = new CacheStore(config.cacheDir(), config.isCacheEnabled());
        Stats stats = new Stats();

        // 1. Scan (deterministic order) and read all source files.
        List<String> files = scan(root);
        stats.filesScanned = files.size();
        Map<String, String> contents = new LinkedHashMap<>();
        Map<String, String> hashes = new HashMap<>();
        Map<String, String> initialHashes = new HashMap<>();
        for (String relPath : files) {
            String content = readFile(root.resolve(relPath));
            contents.put(relPath, content);
            String hash = CacheStore.sha256(content);
            hashes.put(relPath, hash);
            initialHashes.put(relPath, hash);
        }

        ChangeManifest manifest = new ChangeManifest();
        manifest.getWarnings().addAll(configWarnings);

        // Lazily populated parse / declaration state.
        Map<String, ParsedFile> parsed = new HashMap<>();
        Map<String, List<String>> declarations = new HashMap<>();
        Set<String> skipped = new HashSet<>();
        Set<String> changedByUs = new HashSet<>();
        Path solverRoot = root;
        Path parsedWithRoot = null;
        Path snapshotDir = null;

        // Ranges already rewritten by earlier rules, per file (post-edit coordinates).
        Map<String, List<int[]>> touchedRanges = new HashMap<>();

        // 2. Rule passes.
        for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
            RewriteRule rule = rules.get(ruleIndex);
            checkCancellation(deadline);

            if (parsedWithRoot != null && !parsedWithRoot.equals(solverRoot)) {
                parsed.clear(); // solver state of previous parses is stale
                parsedWithRoot = null;
            }

            // Declarations of every parseable file (cached by content hash;
            // only files the cache cannot answer for are parsed).
            ensureDeclarations(files, contents, hashes, parsed, declarations, skipped,
                    changedByUs, solverRoot, cache, manifest, stats, deadline);
            if (parsedWithRoot == null) {
                parsedWithRoot = solverRoot;
            }
            String declHash = declarationsHash(files, skipped, declarations, manifest);

            // Collect edits per file (parallel), incrementally cached.
            Map<String, List<TextEdit>> editsByFile = new TreeMap<>();
            Map<String, List<String>> warningsByFile = new TreeMap<>();
            List<String> parseable = parseableFiles(files, skipped);
            Path currentSolverRoot = solverRoot;
            parallelForEach(parseable, relPath -> {
                checkCancellation(deadline);
                String editKey = CacheStore.sha256(
                        rule.id() + " " + hashes.get(relPath) + " " + declHash);
                var cached = cache.getEdits(editKey);
                List<TextEdit> edits;
                List<String> warnings = new ArrayList<>();
                if (cached.isPresent()) {
                    edits = cached.get();
                    synchronized (stats) {
                        stats.cacheHits++;
                    }
                } else {
                    ParsedFile pf = parsed.get(relPath);
                    if (pf == null) {
                        pf = parseOne(relPath, contents.get(relPath), currentSolverRoot,
                                changedByUs.contains(relPath), stats);
                        synchronized (parsed) {
                            parsed.put(relPath, pf);
                        }
                    }
                    RuleContext ctx = new RuleContext(pf, config.getOnUnresolved(), warnings);
                    edits = new ArrayList<>(rule.collectEdits(pf, ctx));
                    edits.sort(TextEdit::compareTo);
                    cache.putEdits(editKey, edits);
                    synchronized (stats) {
                        stats.cacheMisses++;
                    }
                }
                synchronized (editsByFile) {
                    editsByFile.put(relPath, edits);
                    warningsByFile.put(relPath, warnings);
                }
            });
            for (List<String> w : warningsByFile.values()) {
                manifest.getWarnings().addAll(w);
            }

            // Apply edits file by file, in deterministic order.
            Map<String, String> changedThisPass = new TreeMap<>();
            for (Map.Entry<String, List<TextEdit>> entry : editsByFile.entrySet()) {
                String relPath = entry.getKey();
                List<TextEdit> edits = resolveConflicts(relPath, entry.getValue(), manifest);
                if (edits.isEmpty()) {
                    continue;
                }
                String content = contents.get(relPath);
                List<TextEdit> applicable = new ArrayList<>();
                for (TextEdit edit : edits) {
                    if (crossesEarlierRule(edit, touchedRanges.get(relPath))) {
                        handleCrossRuleConflict(relPath, edit, manifest);
                        continue; // FIRST_WINS drops the edit; FAIL already threw
                    }
                    applicable.add(edit);
                }
                if (applicable.isEmpty()) {
                    continue;
                }
                String newContent = applyEdits(content, applicable);
                for (TextEdit edit : applicable) {
                    int[] lineCol = lineCol(content, edit.offset());
                    manifest.getChanges().add(new ChangeEntry(relPath, lineCol[0], lineCol[1],
                            edit.offset(), edit.length(), edit.oldText(), edit.newText(), edit.ruleId()));
                }
                contents.put(relPath, newContent);
                hashes.put(relPath, CacheStore.sha256(newContent));
                declarations.remove(relPath);
                parsed.remove(relPath);
                changedByUs.add(relPath);
                changedThisPass.put(relPath, newContent);
                List<int[]> touched = touchedRanges.computeIfAbsent(relPath, k -> new ArrayList<>());
                for (TextEdit edit : applicable) {
                    touched.add(new int[]{edit.offset(), edit.offset() + edit.newText().length()});
                }
            }

            // Keep the solver snapshot in sync for subsequent passes.
            if (!changedThisPass.isEmpty() && ruleIndex + 1 < rules.size()) {
                if (snapshotDir == null) {
                    snapshotDir = config.workDir();
                    snapshot(snapshotDir, contents);
                } else {
                    snapshot(snapshotDir, changedThisPass);
                }
                solverRoot = snapshotDir;
            }
        }

        // 3. Final verification: every affected file must still parse.
        if (!changedByUs.isEmpty()) {
            SourceParser verify = new SourceParser(null);
            for (String relPath : files) {
                if (!changedByUs.contains(relPath)) {
                    continue;
                }
                checkCancellation(deadline);
                if (verify.parse(null, relPath, contents.get(relPath)) instanceof ParseOutcome.Err err) {
                    throw new RefactorException("rewrite produced unparseable file "
                            + err.relPath() + ":" + err.line() + ":" + err.column()
                            + " " + err.message());
                }
            }
        }

        // 4. Deterministic task id: identical inputs produce identical ids.
        String taskId = taskId(files, initialHashes, manifest);
        manifest.setTaskId(taskId);

        // 5. Assemble the final change set (only files that actually changed).
        Map<String, String> newContents = new TreeMap<>();
        for (String relPath : files) {
            if (!hashes.get(relPath).equals(initialHashes.get(relPath))) {
                newContents.put(relPath, contents.get(relPath));
            }
        }

        // 5. Commit (apply mode only). Preview touches nothing.
        if (doCommit) {
            checkCancellation(deadline);
            committer.commit(root, newContents, config.journalDir(), taskId);
        }

        return new TaskResult(manifest, newContents, doCommit,
                stats.filesScanned, stats.filesParsed, stats.cacheHits, stats.cacheMisses);
    }

    // ---------------------------------------------------------------- scanning

    private List<String> scan(Path root) {
        if (!Files.isDirectory(root)) {
            throw new RefactorException("source directory does not exist: " + root);
        }
        Path stateDir = config.getStateDir().toAbsolutePath().normalize();
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.toAbsolutePath().normalize().startsWith(stateDir))
                    .filter(p -> {
                        // never descend into hidden directories (.refactor, .git, ...)
                        Path rel = root.relativize(p);
                        for (Path part : rel) {
                            if (part.toString().startsWith(".")) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .forEach(p -> files.add(root.relativize(p).toString().replace('\\', '/')));
        } catch (IOException e) {
            throw new RefactorException("failed to scan " + root, e);
        }
        files.sort(String::compareTo);
        return files;
    }

    private String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RefactorException("cannot read " + file, e);
        }
    }

    // ---------------------------------------------------------------- parsing

    /**
     * Makes sure every parseable file has declarations available, parsing only
     * the files the declaration cache cannot answer for.
     */
    private void ensureDeclarations(List<String> files, Map<String, String> contents,
                                    Map<String, String> hashes, Map<String, ParsedFile> parsed,
                                    Map<String, List<String>> declarations, Set<String> skipped,
                                    Set<String> changedByUs, Path solverRoot, CacheStore cache,
                                    ChangeManifest manifest, Stats stats, long deadline) {
        List<String> needParse = new ArrayList<>();
        for (String relPath : files) {
            if (skipped.contains(relPath)) {
                continue;
            }
            List<String> decls = declarations.get(relPath);
            if (decls == null) {
                decls = cache.getDeclarations(hashes.get(relPath)).orElse(null);
                if (decls != null) {
                    declarations.put(relPath, decls);
                }
            }
            if (decls == null) {
                needParse.add(relPath);
            }
        }
        if (needParse.isEmpty()) {
            return;
        }
        SourceParser parser = new SourceParser(solverRoot);
        Map<String, ParseOutcome> outcomes = new TreeMap<>();
        parallelForEach(needParse, relPath -> {
            checkCancellation(deadline);
            ParseOutcome outcome = parser.parse(null, relPath, contents.get(relPath));
            synchronized (outcomes) {
                outcomes.put(relPath, outcome);
            }
            synchronized (stats) {
                stats.filesParsed++;
            }
        });
        for (Map.Entry<String, ParseOutcome> entry : outcomes.entrySet()) {
            String relPath = entry.getKey();
            ParseOutcome outcome = entry.getValue();
            if (outcome instanceof ParseOutcome.Ok ok) {
                parsed.put(relPath, ok.file());
                List<String> decls = extractDeclarations(ok.file());
                declarations.put(relPath, decls);
                cache.putDeclarations(hashes.get(relPath), decls);
            } else if (outcome instanceof ParseOutcome.Err err) {
                handleParseError(relPath, err, changedByUs.contains(relPath), skipped, manifest);
            }
        }
    }

    private void handleParseError(String relPath, ParseOutcome.Err err, boolean changedByUs,
                                  Set<String> skipped, ChangeManifest manifest) {
        String detail = err.relPath() + ":" + err.line() + ":" + err.column() + " " + err.message();
        if (changedByUs) {
            // Our own edits produced unparseable code: always fatal.
            throw new RefactorException("rewrite produced unparseable file " + detail);
        }
        if (config.getOnParseError() == ParseErrorPolicy.FAIL) {
            throw new RefactorException("unparseable file: " + detail);
        }
        skipped.add(relPath);
        manifest.getSkippedFiles().add(
                new SkippedFile(err.relPath(), err.line(), err.column(), err.message()));
    }

    private ParsedFile parseOne(String relPath, String content, Path solverRoot,
                                boolean changedByUs, Stats stats) {
        ParseOutcome outcome = new SourceParser(solverRoot).parse(null, relPath, content);
        synchronized (stats) {
            stats.filesParsed++;
        }
        if (outcome instanceof ParseOutcome.Ok ok) {
            return ok.file();
        }
        ParseOutcome.Err err = (ParseOutcome.Err) outcome;
        String detail = err.relPath() + ":" + err.line() + ":" + err.column() + " " + err.message();
        if (changedByUs) {
            throw new RefactorException("rewrite produced unparseable file " + detail);
        }
        throw new RefactorException("unparseable file: " + detail);
    }

    private List<String> parseableFiles(List<String> files, Set<String> skipped) {
        List<String> parseable = new ArrayList<>();
        for (String relPath : files) {
            if (!skipped.contains(relPath)) {
                parseable.add(relPath);
            }
        }
        return parseable;
    }

    // ---------------------------------------------------------------- edits

    /** Sorts edits, drops exact duplicates and resolves overlaps per strategy. */
    private List<TextEdit> resolveConflicts(String relPath, List<TextEdit> edits,
                                            ChangeManifest manifest) {
        List<TextEdit> sorted = new ArrayList<>(edits);
        sorted.sort(TextEdit::compareTo);
        List<TextEdit> result = new ArrayList<>();
        TextEdit previous = null;
        for (TextEdit edit : sorted) {
            if (previous != null) {
                if (edit.offset() == previous.offset() && edit.length() == previous.length()
                        && edit.newText().equals(previous.newText())) {
                    continue; // exact duplicate
                }
                if (edit.offset() < previous.end()) {
                    String message = "overlapping edits at offset " + edit.offset()
                            + " (rules " + previous.ruleId() + ", " + edit.ruleId() + ")";
                    if (config.getConflictStrategy() == ConflictStrategy.FAIL) {
                        throw new RefactorException("conflicting edits in " + relPath + ": " + message);
                    }
                    manifest.getConflicts().add(new EditConflict(relPath, message,
                            List.of(previous.ruleId(), edit.ruleId())));
                    manifest.getWarnings().add(relPath + ": dropped conflicting edit of rule "
                            + edit.ruleId() + " at offset " + edit.offset() + " (first rule wins)");
                    continue;
                }
            }
            result.add(edit);
            previous = edit;
        }
        return result;
    }

    private boolean crossesEarlierRule(TextEdit edit, List<int[]> touched) {
        if (touched == null) {
            return false;
        }
        for (int[] range : touched) {
            if (edit.offset() < range[1] && edit.end() > range[0]) {
                return true;
            }
        }
        return false;
    }

    private void handleCrossRuleConflict(String relPath, TextEdit edit, ChangeManifest manifest) {
        String message = "rule '" + edit.ruleId() + "' edits a region already rewritten"
                + " by an earlier rule at offset " + edit.offset();
        if (config.getConflictStrategy() == ConflictStrategy.FAIL) {
            throw new RefactorException("conflicting rules in " + relPath + ": " + message);
        }
        manifest.getConflicts().add(new EditConflict(relPath, message, List.of(edit.ruleId())));
        manifest.getWarnings().add(relPath + ": dropped edit of rule '" + edit.ruleId()
                + "' at offset " + edit.offset() + " (earlier rule wins)");
    }

    /** Applies edits back-to-front; verifies each edit's expected old text. */
    private String applyEdits(String content, List<TextEdit> edits) {
        List<TextEdit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(TextEdit::offset).reversed());
        StringBuilder sb = new StringBuilder(content);
        for (TextEdit edit : sorted) {
            String actual = sb.substring(edit.offset(), edit.end());
            if (!actual.equals(edit.oldText())) {
                throw new RefactorException("internal error: edit of rule " + edit.ruleId()
                        + " expects '" + edit.oldText() + "' but found '" + actual + "'");
            }
            sb.replace(edit.offset(), edit.end(), edit.newText());
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- declarations

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<String> extractDeclarations(ParsedFile pf) {
        List<String> decls = new ArrayList<>();
        for (TypeDeclaration td : pf.cu().findAll(TypeDeclaration.class)) {
            String fqn = (String) td.getFullyQualifiedName().orElse(td.getNameAsString());
            decls.add("type " + fqn);
            for (MethodDeclaration md : (List<MethodDeclaration>) td.getMethods()) {
                decls.add("method " + fqn + "#" + md.getDeclarationAsString(false, false, false));
            }
            for (FieldDeclaration fd : (List<FieldDeclaration>) td.getFields()) {
                for (VariableDeclarator v : fd.getVariables()) {
                    decls.add("field " + fqn + "#" + v.getNameAsString()
                            + ":" + fd.getElementType().asString());
                }
            }
        }
        decls.sort(String::compareTo);
        return decls;
    }

    private String declarationsHash(List<String> files, Set<String> skipped,
                                    Map<String, List<String>> declarations,
                                    ChangeManifest manifest) {
        StringBuilder sb = new StringBuilder();
        for (String relPath : files) {
            if (skipped.contains(relPath)) {
                sb.append("SKIPPED ").append(relPath).append('\n');
                continue;
            }
            sb.append(relPath).append(' ');
            for (String decl : declarations.get(relPath)) {
                sb.append(decl).append('\n');
            }
        }
        return CacheStore.sha256(sb.toString());
    }

    // ---------------------------------------------------------------- misc

    private String taskId(List<String> files, Map<String, String> initialHashes,
                          ChangeManifest manifest) {
        StringBuilder sb = new StringBuilder();
        for (RewriteRule rule : rules) {
            sb.append(rule.id()).append(':').append(rule.order()).append('\n');
        }
        for (String relPath : files) {
            sb.append(relPath).append(' ').append(initialHashes.get(relPath)).append('\n');
        }
        for (ChangeEntry change : manifest.getChanges()) {
            sb.append(change.file()).append(' ').append(change.offset()).append(' ')
                    .append(change.length()).append(' ').append(change.ruleId()).append(' ')
                    .append(CacheStore.sha256(change.newText())).append('\n');
        }
        return CacheStore.sha256(sb.toString()).substring(0, 16);
    }

    private void snapshot(Path dir, Map<String, String> contents) {
        try {
            for (Map.Entry<String, String> entry : contents.entrySet()) {
                Path target = dir.resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RefactorException("failed to update solver snapshot in " + dir, e);
        }
    }

    private static int[] lineCol(String content, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new int[]{line, offset - lineStart + 1};
    }

    private void checkCancellation(long deadline) {
        if (Thread.currentThread().isInterrupted()) {
            throw new TaskCancelledException("task cancelled");
        }
        if (System.currentTimeMillis() > deadline) {
            throw new TaskCancelledException("task timed out after " + config.getTimeoutMillis() + " ms");
        }
    }

    private void parallelForEach(List<String> items, Consumer<String> action) {
        if (items.size() < 2 || config.getParallelism() <= 1) {
            for (String item : items) {
                action.accept(item);
            }
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(config.getParallelism(), items.size()));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (String item : items) {
                futures.add(pool.submit(() -> action.accept(item)));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TaskCancelledException("task cancelled");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RefactorException("parallel task failed", cause);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static class Stats {
        int filesScanned;
        int filesParsed;
        int cacheHits;
        int cacheMisses;
    }
}
