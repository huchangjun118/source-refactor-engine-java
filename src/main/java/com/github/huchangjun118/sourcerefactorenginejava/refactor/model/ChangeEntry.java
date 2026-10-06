package com.github.huchangjun118.sourcerefactorenginejava.refactor.model;

/**
 * One entry of the change manifest: a concrete, auditable change with
 * file, position (1-based line/column) and before/after content.
 */
public record ChangeEntry(String file, int line, int column, int offset, int length,
                          String oldText, String newText, String ruleId) {
}
