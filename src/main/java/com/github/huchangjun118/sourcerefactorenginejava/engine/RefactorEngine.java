package com.github.huchangjun118.sourcerefactorenginejava.engine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.huchangjun118.sourcerefactorenginejava.engine.edit.EditApplier;
import com.github.huchangjun118.sourcerefactorenginejava.engine.edit.TextEdit;
import com.github.huchangjun118.sourcerefactorenginejava.engine.inc.DependencyRecorder;
import com.github.huchangjun118.sourcerefactorenginejava.engine.inc.IncrementalCache;
import com.github.huchangjun118.sourcerefactorenginejava.engine.parse.ParsedProject;
import com.github.huchangjun118.sourcerefactorenginejava.engine.parse.ProjectParser;
import com.github.huchangjun118.sourcerefactorenginejava.engine.plan.ChangeItem;
import com.github.huchangjun118.sourcerefactorenginejava.engine.plan.ConflictRecord;
import com.github.huchangjun118.sourcerefactorenginejava.engine.plan.SkippedFile;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.Resolve;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RewriteRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RuleContext;
import com.github.huchangjun118.sourcerefactorenginejava.engine.tx.FileCommitter;
import com.github.huchangjun118.sourcerefactorenginejava.engine.tx.JournalManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestrates a refactor task: scan -> parse -> plan -> verify -> commit.
 *
 * <p>Preview and apply share the exact same planning code path, so a preview
 * is always bit-for-bit what apply would do. Writes happen only at the very
 * end through {@link FileCommitter}; any failure before that point leaves
 * the project untouched.
 */
public class RefactorEngine {
    private final ProjectParser parser = new ProjectParser();
    private final JournalManager journalManager = new JournalManager();
    private final FileCommitter committer;

    public RefactorEngine() {
        this(new FileCommitter());
    }

    /** Visible for tests: allows injecting a failing committer. */
    public RefactorEngine(FileCommitter committer) {
        this.committer = committer;
    }

    /** Compute the change plan without touching the disk. */
    public RefactorReport preview(Path projectRoot, List<RewriteRule> rules, RefactorOptions options) {
        return run(projectRoot, rules, options, false);
    }

    /** Compute the same plan as preview and commit it all-or-nothing. */
    public RefactorReport apply(Path projectRoot, List<RewriteRule> rules, RefactorOptions options) {
        return run(projectRoot, rules, options, true);
    }

    /** Roll back a previously applied task by journal id. */
    public void rollback(Path projectRoot, String journalId) {
        journalManager.rollback(projectRoot.toAbsolutePath().normalize(), journalId);
    }

    private RefactorReport run(Path projectRoot, List<RewriteRule> rules,
                               RefactorOptions options, boolean doApply) {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<RewriteRule> sortedRules = new ArrayList<>(rules);
        sortedRules.sort(Comparator.comparingInt(RewriteRule::order).thenComparing(RewriteRule::id));

        List<Path> files = scanJavaFiles(root);
        Map<Path, String> contents = readAll(files);
        Map<Path, String> hashes = hashAll(contents);

        ParsedProject parsed = parser.parse(root, files, options.getParseErrorPolicy());
        Map<Path, String> packages = packagesOf(parsed, files);

        String ruleSetHash = ruleSetHash(sortedRules);
        IncrementalCache cache = options.isIncremental()
                ? IncrementalCache.load(root) : IncrementalCache.empty();
        Set<Path> stale = options.isIncremental()
                ? cache.computeStale(hashes, packages, ruleSetHash) : hashes.keySet();
        // Files that failed to parse never receive edits and stay byte-identical.
        stale.retainAll(parsed.getUnits().keySet());

        RuleContext context = new RuleContext(parsed.getUnits(), parsed.getOriginalContents());
        Map<String, Path> typeIndex = new HashMap<>();
        Map<String, TypeDeclaration<?>> typeAst = new HashMap<>();
        indexTypes(parsed, typeIndex, typeAst);

        Map<Path, List<TextEdit>> editsByFile = new TreeMap<>();
        int reused = 0;
        for (Path file : parsed.getUnits().keySet()) {
            if (!stale.contains(file)) {
                editsByFile.put(file, cache.cachedEdits(file));
                reused++;
            }
        }
        Map<Path, FileComputation> computed =
                computeEdits(stale, sortedRules, context, typeIndex, typeAst, options);
        for (Map.Entry<Path, FileComputation> e : computed.entrySet()) {
            editsByFile.put(e.getKey(), e.getValue().edits);
        }

        // Merge edits per file: sort, dedupe, resolve conflicts deterministically.
        MergeResult merge = merge(editsByFile, root);

        // Produce new contents and verify every touched file still parses.
        Map<Path, String> newContents = new TreeMap<>();
        JavaParser verifier = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        for (Map.Entry<Path, List<TextEdit>> e : merge.keptEdits.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            String original = parsed.getOriginalContents().get(e.getKey());
            String rewritten = EditApplier.apply(original, e.getValue());
            if (!rewritten.equals(original)) {
                if (!verifier.parse(rewritten).isSuccessful()) {
                    throw new RefactorTaskException(
                            "rewrite produced an unparseable file: " + e.getKey()
                                    + " (task aborted, nothing written)");
                }
                newContents.put(e.getKey(), rewritten);
            }
        }

        RefactorReport report = buildReport(root, parsed, merge, newContents, files.size(), reused);

        // Update cache entries for recomputed files.
        for (Map.Entry<Path, FileComputation> e : computed.entrySet()) {
            Path file = e.getKey();
            cache.updateFile(file, hashes.get(file), packages.get(file),
                    e.getValue().edits, e.getValue().deps, e.getValue().dependsOnAll);
        }
        cache.retainOnly(packages);

        if (!doApply) {
            return report;
        }
        if (newContents.isEmpty()) {
            if (options.isIncremental()) {
                cache.store(root, ruleSetHash);
            }
            report.setApplied(true);
            return report;
        }

        Map<Path, String> originalsOfChanged = new LinkedHashMap<>();
        for (Path file : newContents.keySet()) {
            originalsOfChanged.put(file, parsed.getOriginalContents().get(file));
        }
        String journalId = journalManager.createJournal(root, originalsOfChanged);
        committer.commit(newContents);

        // Refresh cache to the post-commit state: changed files now hash to
        // their new content and (rename rules are idempotent) yield no edits.
        for (Map.Entry<Path, String> e : newContents.entrySet()) {
            cache.updateFile(e.getKey(), sha256(e.getValue()), packages.get(e.getKey()),
                    List.of(), computed.containsKey(e.getKey())
                            ? computed.get(e.getKey()).deps : Set.of(),
                    false);
        }
        if (options.isIncremental()) {
            cache.store(root, ruleSetHash);
        }
        report.setApplied(true);
        report.setJournalId(journalId);
        return report;
    }

    // ---------------------------------------------------------------- phases

    private static List<Path> scanJavaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(root.resolve(IncrementalCache.STATE_DIR)))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot scan " + root, e);
        }
    }

    private static Map<Path, String> readAll(List<Path> files) {
        Map<Path, String> contents = new LinkedHashMap<>();
        for (Path file : files) {
            try {
                contents.put(file, Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
        }
        return contents;
    }

    private static Map<Path, String> hashAll(Map<Path, String> contents) {
        Map<Path, String> hashes = new LinkedHashMap<>();
        contents.forEach((file, content) -> hashes.put(file, sha256(content)));
        return hashes;
    }

    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<Path, String> packagesOf(ParsedProject parsed, List<Path> files) {
        Map<Path, String> packages = new HashMap<>();
        for (Path file : files) {
            packages.put(file, "");
        }
        parsed.getUnits().forEach((file, cu) ->
                packages.put(file, cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString()).orElse("")));
        return packages;
    }

    private static String ruleSetHash(List<RewriteRule> sortedRules) {
        String joined = sortedRules.stream().map(RewriteRule::fingerprint)
                .collect(Collectors.joining("\n"));
        return sha256(joined);
    }

    private static void indexTypes(ParsedProject parsed, Map<String, Path> typeIndex,
                                   Map<String, TypeDeclaration<?>> typeAst) {
        parsed.getUnits().forEach((file, cu) -> {
            for (TypeDeclaration<?> td : cu.getTypes()) {
                td.getFullyQualifiedName().ifPresent(qn -> {
                    typeIndex.put(qn, file);
                    typeAst.put(qn, td);
                });
            }
        });
    }

    private static final class FileComputation {
        List<TextEdit> edits = new ArrayList<>();
        Set<Path> deps = Set.of();
        boolean dependsOnAll;
    }

    /** Compute edits for all stale files concurrently, honouring the timeout. */
    private Map<Path, FileComputation> computeEdits(
            Set<Path> stale, List<RewriteRule> rules, RuleContext context,
            Map<String, Path> typeIndex, Map<String, TypeDeclaration<?>> typeAst,
            RefactorOptions options) {
        Map<Path, FileComputation> result = new TreeMap<>();
        if (stale.isEmpty()) {
            return result;
        }
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.max(1, Math.min(options.getConcurrency(), stale.size())));
        try {
            List<Path> staleSorted = new ArrayList<>(stale);
            staleSorted.sort(Path::compareTo);
            List<Callable<Map.Entry<Path, FileComputation>>> tasks = new ArrayList<>();
            for (Path file : staleSorted) {
                tasks.add(() -> {
                    FileComputation computation = new FileComputation();
                    DependencyRecorder recorder = new DependencyRecorder(typeIndex, typeAst);
                    CompilationUnit cu = context.getUnits().get(file);
                    Resolve.withListener(recorder, () -> {
                        for (RewriteRule rule : rules) {
                            computation.edits.addAll(rule.computeEdits(file, cu, context));
                        }
                    });
                    computation.deps = recorder.getDeps();
                    computation.dependsOnAll = recorder.isDependsOnAll();
                    return Map.entry(file, computation);
                });
            }
            List<Future<Map.Entry<Path, FileComputation>>> futures =
                    pool.invokeAll(tasks, options.getTimeoutSeconds(), TimeUnit.SECONDS);
            for (Future<Map.Entry<Path, FileComputation>> future : futures) {
                if (future.isCancelled()) {
                    throw new RefactorTaskException(
                            "refactor task timed out after " + options.getTimeoutSeconds()
                                    + "s; cancelled, nothing written");
                }
                try {
                    Map.Entry<Path, FileComputation> entry = future.get();
                    result.put(entry.getKey(), entry.getValue());
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RefactorTaskException("edit computation failed: " + cause, cause);
                }
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RefactorTaskException("refactor task interrupted; nothing written", e);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- merge

    private static final class MergeResult {
        Map<Path, List<TextEdit>> keptEdits = new TreeMap<>();
        List<ConflictRecord> conflicts = new ArrayList<>();
    }

    /**
     * Deterministic conflict strategy: edits are sorted by (start, end,
     * ruleOrder, ruleId); exact duplicates collapse; overlapping edits with
     * different content form a conflict cluster where the edit from the
     * lowest-order rule wins and the rest are dropped and reported.
     */
    private MergeResult merge(Map<Path, List<TextEdit>> editsByFile, Path root) {
        MergeResult result = new MergeResult();
        for (Map.Entry<Path, List<TextEdit>> e : editsByFile.entrySet()) {
            Path file = e.getKey();
            List<TextEdit> sorted = new ArrayList<>(e.getValue());
            sorted.sort(TextEdit::compareTo);

            List<TextEdit> deduped = new ArrayList<>();
            for (TextEdit edit : sorted) {
                if (!deduped.isEmpty()) {
                    TextEdit last = deduped.get(deduped.size() - 1);
                    if (last.getStart() == edit.getStart() && last.getEnd() == edit.getEnd()
                            && last.getNewText().equals(edit.getNewText())) {
                        continue; // identical edit from two rules: apply once
                    }
                }
                deduped.add(edit);
            }

            List<TextEdit> kept = new ArrayList<>();
            int i = 0;
            while (i < deduped.size()) {
                // Gather the transitively overlapping cluster.
                List<TextEdit> cluster = new ArrayList<>();
                cluster.add(deduped.get(i));
                int clusterEnd = deduped.get(i).getEnd();
                int j = i + 1;
                while (j < deduped.size() && deduped.get(j).getStart() < clusterEnd) {
                    cluster.add(deduped.get(j));
                    clusterEnd = Math.max(clusterEnd, deduped.get(j).getEnd());
                    j++;
                }
                TextEdit winner = cluster.stream()
                        .min(Comparator.comparingInt(TextEdit::getRuleOrder)
                                .thenComparing(TextEdit::getRuleId))
                        .orElseThrow();
                kept.add(winner);
                if (cluster.size() > 1) {
                    List<String> ruleIds = cluster.stream().map(TextEdit::getRuleId)
                            .distinct().sorted().collect(Collectors.toList());
                    result.conflicts.add(new ConflictRecord(
                            root.relativize(file).toString(), winner.getStart(), ruleIds,
                            winner.getRuleId(),
                            "kept edit from lowest-order rule '" + winner.getRuleId()
                                    + "', dropped " + (cluster.size() - 1) + " overlapping edit(s)"));
                }
                i = j;
            }
            result.keptEdits.put(file, kept);
        }
        result.conflicts.sort(Comparator.comparing(ConflictRecord::getFile)
                .thenComparingInt(ConflictRecord::getOffset));
        return result;
    }

    // ---------------------------------------------------------------- report

    private RefactorReport buildReport(Path root, ParsedProject parsed, MergeResult merge,
                                       Map<Path, String> newContents,
                                       int filesScanned, int reused) {
        RefactorReport report = new RefactorReport();
        report.setTaskId(UUID.randomUUID().toString());
        report.setFilesScanned(filesScanned);
        report.setFilesChanged(newContents.size());
        report.setFilesReusedFromCache(reused);
        report.getConflicts().addAll(merge.conflicts);
        parsed.getIssues().forEach(issue -> report.getSkippedFiles().add(
                new SkippedFile(root.relativize(Path.of(issue.getFile())).toString(),
                        issue.getMessage(), issue.getLine(), issue.getColumn())));

        Map<Path, List<TextEdit>> kept = merge.keptEdits;
        for (Path file : newContents.keySet()) {
            String original = parsed.getOriginalContents().get(file);
            int[] lineStarts = lineStarts(original);
            String relative = root.relativize(file).toString();
            for (TextEdit edit : kept.get(file)) {
                int line = lineOf(lineStarts, edit.getStart());
                int column = edit.getStart() - lineStarts[line - 1] + 1;
                report.getChanges().add(new ChangeItem(relative, line, column, edit.getStart(),
                        original.substring(edit.getStart(), edit.getEnd()),
                        edit.getNewText(), edit.getRuleId()));
            }
        }
        report.getChanges().sort(Comparator.comparing(ChangeItem::getFile)
                .thenComparingInt(ChangeItem::getOffset));
        return report;
    }

    private static int[] lineStarts(String content) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int lineOf(int[] lineStarts, int offset) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (lineStarts[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo + 1;
    }
}
