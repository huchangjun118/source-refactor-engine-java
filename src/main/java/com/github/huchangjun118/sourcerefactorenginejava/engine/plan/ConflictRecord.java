package com.github.huchangjun118.sourcerefactorenginejava.engine.plan;

import java.util.List;

/** Records a conflict between rules editing the same location. */
public class ConflictRecord {
    private String file;
    private int offset;
    private List<String> ruleIds;
    private String winnerRuleId;
    private String resolution;

    public ConflictRecord() {}

    public ConflictRecord(String file, int offset, List<String> ruleIds, String winnerRuleId, String resolution) {
        this.file = file;
        this.offset = offset;
        this.ruleIds = ruleIds;
        this.winnerRuleId = winnerRuleId;
        this.resolution = resolution;
    }

    public String getFile() { return file; }
    public int getOffset() { return offset; }
    public List<String> getRuleIds() { return ruleIds; }
    public String getWinnerRuleId() { return winnerRuleId; }
    public String getResolution() { return resolution; }
}
