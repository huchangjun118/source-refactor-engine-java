package com.github.huchangjun118.sourcerefactorenginejava.engine.parse;

import java.util.List;

/** Thrown when parse errors are found and the policy is FAIL. */
public class ProjectParseException extends RuntimeException {
    private final List<ParseIssue> issues;

    public ProjectParseException(List<ParseIssue> issues) {
        super("failed to parse " + issues.size() + " file(s): "
                + (issues.isEmpty() ? "" : issues.get(0).getFile()
                + (issues.get(0).getLine() != null
                    ? " at line " + issues.get(0).getLine() + ", column " + issues.get(0).getColumn()
                    : "")
                + ": " + issues.get(0).getMessage()));
        this.issues = issues;
    }

    public List<ParseIssue> getIssues() { return issues; }
}
