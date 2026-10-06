package com.github.huchangjun118.sourcerefactorenginejava.refactor.parse;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.TextEdit;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;

/**
 * A successfully parsed source file with its exact original content and a
 * line index for offset/position conversion.
 */
public class ParsedFile {

    private final Path path;
    private final String relPath;
    private final String content;
    private final CompilationUnit cu;
    private final int[] lineStarts;

    public ParsedFile(Path path, String relPath, String content, CompilationUnit cu) {
        this.path = path;
        this.relPath = relPath;
        this.content = content;
        this.cu = cu;
        this.lineStarts = computeLineStarts(content);
    }

    private static int[] computeLineStarts(String s) {
        int[] tmp = new int[s.length() + 2];
        int n = 0;
        tmp[n++] = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                tmp[n++] = i + 1;
            }
        }
        int[] result = new int[n];
        System.arraycopy(tmp, 0, result, 0, n);
        return result;
    }

    /** Character offset of a 1-based line/column position. */
    public int offset(Position p) {
        int line = p.line;
        if (line < 1 || line > lineStarts.length) {
            throw new IllegalArgumentException("line out of range: " + line + " in " + relPath);
        }
        return lineStarts[line - 1] + (p.column - 1);
    }

    /** 1-based line/column position of a character offset. */
    public Position position(int offset) {
        int lo = 0, hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= offset) lo = mid; else hi = mid - 1;
        }
        return new Position(lo + 1, offset - lineStarts[lo] + 1);
    }

    /** Build a verified edit replacing the exact text covered by the given range. */
    public TextEdit edit(Range range, String newText, String ruleId) {
        int start = offset(range.begin);
        int end = offset(range.end) + 1; // JavaParser end column is inclusive
        String oldText = content.substring(start, end);
        return new TextEdit(relPath, start, end - start, oldText, newText, ruleId);
    }

    public Path path() {
        return path;
    }

    public String relPath() {
        return relPath;
    }

    public String content() {
        return content;
    }

    public CompilationUnit cu() {
        return cu;
    }
}
