package com.github.huchangjun118.sourcerefactorenginejava.engine.plan;

/** A single textual change inside one file. */
public class ChangeItem {
    private String file;
    private int line;
    private int column;
    private int offset;
    private String oldText;
    private String newText;
    private String ruleId;

    public ChangeItem() {}

    public ChangeItem(String file, int line, int column, int offset,
                      String oldText, String newText, String ruleId) {
        this.file = file;
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.oldText = oldText;
        this.newText = newText;
        this.ruleId = ruleId;
    }

    public String getFile() { return file; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public int getOffset() { return offset; }
    public String getOldText() { return oldText; }
    public String getNewText() { return newText; }
    public String getRuleId() { return ruleId; }
}
