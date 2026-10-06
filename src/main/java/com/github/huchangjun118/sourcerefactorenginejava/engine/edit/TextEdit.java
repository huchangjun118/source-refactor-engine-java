package com.github.huchangjun118.sourcerefactorenginejava.engine.edit;

/** A replacement of a character range [start, end) with new text. */
public class TextEdit implements Comparable<TextEdit> {
    private final int start;
    private final int end;
    private final String newText;
    private final String ruleId;
    private final int ruleOrder;

    public TextEdit(int start, int end, String newText, String ruleId, int ruleOrder) {
        this.start = start;
        this.end = end;
        this.newText = newText;
        this.ruleId = ruleId;
        this.ruleOrder = ruleOrder;
    }

    public int getStart() { return start; }
    public int getEnd() { return end; }
    public String getNewText() { return newText; }
    public String getRuleId() { return ruleId; }
    public int getRuleOrder() { return ruleOrder; }

    public boolean overlaps(TextEdit other) {
        return this.start < other.end && other.start < this.end;
    }

    @Override
    public int compareTo(TextEdit o) {
        int c = Integer.compare(start, o.start);
        if (c != 0) return c;
        c = Integer.compare(end, o.end);
        if (c != 0) return c;
        c = Integer.compare(ruleOrder, o.ruleOrder);
        if (c != 0) return c;
        return ruleId.compareTo(o.ruleId);
    }
}
