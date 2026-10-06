package com.github.huchangjun118.sourcerefactorenginejava.engine.inc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.huchangjun118.sourcerefactorenginejava.engine.edit.TextEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Content-hash based incremental cache. A file's cached edits are reused
 * only when its own content, the rule set, its package membership and all
 * files its symbol resolution touched are unchanged; otherwise the file is
 * recomputed. The cache file is written atomically (temp + move) so a crash
 * or cancellation never leaves a corrupt cache.
 */
public class IncrementalCache {
    public static final String STATE_DIR = ".refactor-engine";
    private static final String CACHE_FILE = "cache.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CacheData data;

    private IncrementalCache(CacheData data) {
        this.data = data;
    }

    public static IncrementalCache empty() {
        return new IncrementalCache(new CacheData());
    }

    /** Load cache from disk; missing or corrupt cache yields an empty one. */
    public static IncrementalCache load(Path projectRoot) {
        Path file = cacheFile(projectRoot);
        if (!Files.isRegularFile(file)) {
            return empty();
        }
        try {
            return new IncrementalCache(MAPPER.readValue(file.toFile(), CacheData.class));
        } catch (IOException e) {
            return empty();
        }
    }

    private static Path cacheFile(Path projectRoot) {
        return projectRoot.resolve(STATE_DIR).resolve(CACHE_FILE);
    }

    /**
     * Determine which files must be recomputed.
     *
     * @param currentHashes   file -> content hash
     * @param currentPackages file -> package name
     * @param ruleSetHash     fingerprint of the active rule set
     */
    public Set<Path> computeStale(Map<Path, String> currentHashes,
                                  Map<Path, String> currentPackages,
                                  String ruleSetHash) {
        Set<Path> stale = new LinkedHashSet<>();
        Map<String, List<String>> currentMembers = packageMembers(currentPackages);

        boolean anyStructuralChange = !ruleSetHash.equals(data.getRuleSetHash())
                || !currentHashes.keySet().equals(pathsOf(data.getFiles()))
                || !currentMembers.equals(data.getPackageMembers());

        if (!ruleSetHash.equals(data.getRuleSetHash())) {
            stale.addAll(currentHashes.keySet());
            return stale;
        }

        Map<String, CachedFile> cached = data.getFiles();
        for (Map.Entry<Path, String> e : currentHashes.entrySet()) {
            Path path = e.getKey();
            CachedFile entry = cached.get(path.toString());
            if (entry == null) {
                stale.add(path); // new file
                continue;
            }
            if (!entry.getHash().equals(e.getValue())) {
                stale.add(path); // changed content
                continue;
            }
            List<String> cachedMembers = data.getPackageMembers().get(entry.getPkg());
            List<String> nowMembers = currentMembers.get(currentPackages.get(path));
            if (cachedMembers == null || !cachedMembers.equals(nowMembers)) {
                stale.add(path); // file added/removed in its package
                continue;
            }
            if (entry.isDependsOnAll() && anyStructuralChange) {
                stale.add(path);
            }
        }

        // Propagate staleness along recorded resolution dependencies.
        Map<String, Path> byString = new HashMap<>();
        for (Path p : currentHashes.keySet()) {
            byString.put(p.toString(), p);
        }
        Set<String> staleStrings = new HashSet<>();
        for (Path p : stale) {
            staleStrings.add(p.toString());
        }
        // Deleted files also invalidate their dependents.
        for (String cachedPath : cached.keySet()) {
            if (!byString.containsKey(cachedPath)) {
                staleStrings.add(cachedPath);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, CachedFile> e : cached.entrySet()) {
                if (staleStrings.contains(e.getKey())) {
                    continue;
                }
                for (String dep : e.getValue().getDeps()) {
                    if (staleStrings.contains(dep)) {
                        staleStrings.add(e.getKey());
                        Path p = byString.get(e.getKey());
                        if (p != null) {
                            stale.add(p);
                        }
                        changed = true;
                        break;
                    }
                }
            }
        }
        return stale;
    }

    /** Edits cached for a file that was deemed fresh. */
    public List<TextEdit> cachedEdits(Path file) {
        CachedFile entry = data.getFiles().get(file.toString());
        List<TextEdit> edits = new ArrayList<>();
        if (entry != null) {
            for (CachedEdit ce : entry.getEdits()) {
                edits.add(new TextEdit(ce.getStart(), ce.getEnd(), ce.getNewText(),
                        ce.getRuleId(), ce.getRuleOrder()));
            }
        }
        return edits;
    }

    /** Replace the cache entry of one file after recomputation. */
    public void updateFile(Path file, String hash, String pkg, List<TextEdit> edits,
                           Set<Path> deps, boolean dependsOnAll) {
        CachedFile entry = new CachedFile();
        entry.setHash(hash);
        entry.setPkg(pkg);
        entry.setDependsOnAll(dependsOnAll);
        List<String> depStrings = new ArrayList<>();
        for (Path dep : deps) {
            if (!dep.equals(file)) {
                depStrings.add(dep.toString());
            }
        }
        depStrings.sort(String::compareTo);
        entry.setDeps(depStrings);
        List<CachedEdit> cachedEdits = new ArrayList<>();
        for (TextEdit edit : edits) {
            cachedEdits.add(new CachedEdit(edit.getStart(), edit.getEnd(), edit.getNewText(),
                    edit.getRuleId(), edit.getRuleOrder()));
        }
        entry.setEdits(cachedEdits);
        data.getFiles().put(file.toString(), entry);
    }

    /** Drop entries for files that no longer exist and refresh package members. */
    public void retainOnly(Map<Path, String> currentPackages) {
        data.getFiles().keySet().retainAll(pathStrings(currentPackages.keySet()));
        data.setPackageMembers(packageMembers(currentPackages));
    }

    /** Atomically persist the cache; a crash mid-write never corrupts it. */
    public void store(Path projectRoot, String ruleSetHash) {
        data.setRuleSetHash(ruleSetHash);
        Path file = cacheFile(projectRoot);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(CACHE_FILE + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("failed to store cache " + file, e);
        }
    }

    private static Map<String, List<String>> packageMembers(Map<Path, String> currentPackages) {
        Map<String, List<String>> members = new TreeMap<>();
        for (Map.Entry<Path, String> e : currentPackages.entrySet()) {
            members.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey().toString());
        }
        members.values().forEach(list -> list.sort(String::compareTo));
        return members;
    }

    private static Set<String> pathStrings(Set<Path> paths) {
        Set<String> out = new HashSet<>();
        for (Path p : paths) {
            out.add(p.toString());
        }
        return out;
    }

    private static Set<Path> pathsOf(Map<String, CachedFile> files) {
        Set<Path> out = new HashSet<>();
        for (String s : files.keySet()) {
            out.add(Path.of(s));
        }
        return out;
    }
}
