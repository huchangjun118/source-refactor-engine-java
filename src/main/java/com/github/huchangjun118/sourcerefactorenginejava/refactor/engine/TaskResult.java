package com.github.huchangjun118.sourcerefactorenginejava.refactor.engine;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.ChangeManifest;

import java.util.Map;

/**
 * Result of a preview or apply run. {@link #newContents()} holds the full new
 * content of every changed file (identical between preview and apply).
 */
public class TaskResult {

    private final ChangeManifest manifest;
    private final Map<String, String> newContents;
    private final boolean committed;
    private final int filesScanned;
    private final int filesParsed;
    private final int cacheHits;
    private final int cacheMisses;

    public TaskResult(ChangeManifest manifest, Map<String, String> newContents, boolean committed,
                      int filesScanned, int filesParsed, int cacheHits, int cacheMisses) {
        this.manifest = manifest;
        this.newContents = newContents;
        this.committed = committed;
        this.filesScanned = filesScanned;
        this.filesParsed = filesParsed;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
    }

    public ChangeManifest manifest() {
        return manifest;
    }

    public Map<String, String> newContents() {
        return newContents;
    }

    public boolean committed() {
        return committed;
    }

    public int filesScanned() {
        return filesScanned;
    }

    public int filesParsed() {
        return filesParsed;
    }

    public int cacheHits() {
        return cacheHits;
    }

    public int cacheMisses() {
        return cacheMisses;
    }
}
