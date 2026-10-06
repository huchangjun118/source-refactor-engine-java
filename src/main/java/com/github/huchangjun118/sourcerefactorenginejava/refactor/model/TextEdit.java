package com.github.huchangjun118.sourcerefactorenginejava.refactor.model;

/**
 * A single deterministic text replacement inside one source file.
 * Offsets are character offsets into the file content.
 */
public record TextEdit(String file, int offset, int length, String oldText, String newText, String ruleId)
        implements Comparable<TextEdit> {

    public int end() {
        return offset + length;
    }

    @Override
    public int compareTo(TextEdit other) {
        int c = file.compareTo(other.file);
        if (c != 0) return c;
        c = Integer.compare(offset, other.offset);
        if (c != 0) return c;
        c = Integer.compare(length, other.length);
        if (c != 0) return c;
        c = ruleId.compareTo(other.ruleId);
        if (c != 0) return c;
        return newText.compareTo(other.newText);
    }
}
