package com.github.huchangjun118.sourcerefactorenginejava.api;

/** JSON request for rollback. */
public class RollbackRequest {
    private String projectDir;
    private String journalId;

    public String getProjectDir() { return projectDir; }
    public void setProjectDir(String projectDir) { this.projectDir = projectDir; }
    public String getJournalId() { return journalId; }
    public void setJournalId(String journalId) { this.journalId = journalId; }
}
