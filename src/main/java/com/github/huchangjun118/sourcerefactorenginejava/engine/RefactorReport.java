package com.github.huchangjun118.sourcerefactorenginejava.engine;

import com.github.huchangjun118.sourcerefactorenginejava.engine.plan.ChangeItem;
import com.github.huchangjun118.sourcerefactorenginejava.engine.plan.ConflictRecord;
import com.github.huchangjun118.sourcerefactorenginejava.engine.plan.SkippedFile;

import java.util.ArrayList;
import java.util.List;

/** Outcome of a preview or apply run. */
public class RefactorReport {
    private String taskId;
    private boolean applied;
    private String journalId;
    private final List<ChangeItem> changes = new ArrayList<>();
    private final List<ConflictRecord> conflicts = new ArrayList<>();
    private final List<SkippedFile> skippedFiles = new ArrayList<>();
    private int filesScanned;
    private int filesChanged;
    private int filesReusedFromCache;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public boolean isApplied() { return applied; }
    public void setApplied(boolean applied) { this.applied = applied; }
    public String getJournalId() { return journalId; }
    public void setJournalId(String journalId) { this.journalId = journalId; }
    public List<ChangeItem> getChanges() { return changes; }
    public List<ConflictRecord> getConflicts() { return conflicts; }
    public List<SkippedFile> getSkippedFiles() { return skippedFiles; }
    public int getFilesScanned() { return filesScanned; }
    public void setFilesScanned(int filesScanned) { this.filesScanned = filesScanned; }
    public int getFilesChanged() { return filesChanged; }
    public void setFilesChanged(int filesChanged) { this.filesChanged = filesChanged; }
    public int getFilesReusedFromCache() { return filesReusedFromCache; }
    public void setFilesReusedFromCache(int filesReusedFromCache) { this.filesReusedFromCache = filesReusedFromCache; }
}
