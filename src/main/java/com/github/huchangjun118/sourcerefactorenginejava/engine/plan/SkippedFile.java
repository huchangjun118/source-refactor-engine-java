package com.github.huchangjun118.sourcerefactorenginejava.engine.plan;

/** A file that was skipped (e.g. parse failure) and kept as-is. */
public class SkippedFile {
    private String file;
    private String reason;
    private Integer line;
    private Integer column;

    public SkippedFile() {}

    public SkippedFile(String file, String reason, Integer line, Integer column) {
        this.file = file;
        this.reason = reason;
        this.line = line;
        this.column = column;
    }

    public String getFile() { return file; }
    public String getReason() { return reason; }
    public Integer getLine() { return line; }
    public Integer getColumn() { return column; }
}
