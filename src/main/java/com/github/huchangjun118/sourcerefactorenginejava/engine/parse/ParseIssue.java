package com.github.huchangjun118.sourcerefactorenginejava.engine.parse;

/** A parse problem tied to a file and (optionally) a position. */
public class ParseIssue {
    private final String file;
    private final String message;
    private final Integer line;
    private final Integer column;

    public ParseIssue(String file, String message, Integer line, Integer column) {
        this.file = file;
        this.message = message;
        this.line = line;
        this.column = column;
    }

    public String getFile() { return file; }
    public String getMessage() { return message; }
    public Integer getLine() { return line; }
    public Integer getColumn() { return column; }
}
