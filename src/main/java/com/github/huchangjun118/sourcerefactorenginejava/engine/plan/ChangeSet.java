package com.github.huchangjun118.sourcerefactorenginejava.engine.plan;

import java.util.ArrayList;
import java.util.List;

/** The full, deterministic plan of a refactor task. */
public class ChangeSet {
    private final List<ChangeItem> changes = new ArrayList<>();
    private final List<ConflictRecord> conflicts = new ArrayList<>();
    private final List<SkippedFile> skippedFiles = new ArrayList<>();

    public List<ChangeItem> getChanges() { return changes; }
    public List<ConflictRecord> getConflicts() { return conflicts; }
    public List<SkippedFile> getSkippedFiles() { return skippedFiles; }

    public boolean isEmpty() { return changes.isEmpty(); }
}
