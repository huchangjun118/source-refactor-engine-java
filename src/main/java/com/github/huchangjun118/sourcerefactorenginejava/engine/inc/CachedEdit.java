package com.github.huchangjun118.sourcerefactorenginejava.engine.inc;

/** Serializable form of a text edit. */
public class CachedEdit {
    private int start;
    private int end;
    private String newText;
    private String ruleId;
    private int ruleOrder;

    public CachedEdit() {}

    public CachedEdit(int start, int end, String newText, String ruleId, int ruleOrder) {
        this.start = start;
        this.end = end;
        this.newText = newText;
        this.ruleId = ruleId;
        this.ruleOrder = ruleOrder;
    }

    public int getStart() { return start; }
    public void setStart(int start) { this.start = start; }
    public int getEnd() { return end; }
    public void setEnd(int end) { this.end = end; }
    public String getNewText() { return newText; }
    public void setNewText(String newText) { this.newText = newText; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public int getRuleOrder() { return ruleOrder; }
    public void setRuleOrder(int ruleOrder) { this.ruleOrder = ruleOrder; }
}
