package com.github.huchangjun118.sourcerefactorenginejava.engine.rule;

import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.huchangjun118.sourcerefactorenginejava.engine.edit.TextEdit;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Context handed to rules: parsed ASTs plus original contents of the project. */
public class RuleContext {
    private final Map<Path, CompilationUnit> units;
    private final Map<Path, String> contents;
    private final Map<Path, int[]> lineStartCache = new HashMap<>();

    public RuleContext(Map<Path, CompilationUnit> units, Map<Path, String> contents) {
        this.units = units;
        this.contents = contents;
    }

    public Map<Path, CompilationUnit> getUnits() { return units; }
    public Map<Path, String> getContents() { return contents; }

    /** Build a replace-edit for the given 1-based line/column range (end column inclusive). */
    public synchronized TextEdit replace(Path file, Range range, String newText,
                                         String ruleId, int ruleOrder) {
        int[] lineStarts = lineStartCache.computeIfAbsent(file, f -> lineStarts(contents.get(f)));
        int start = offset(lineStarts, range.begin.line, range.begin.column);
        // JavaParser ranges are inclusive of the end column; TextEdit ends are exclusive.
        int end = offset(lineStarts, range.end.line, range.end.column) + 1;
        return new TextEdit(start, end, newText, ruleId, ruleOrder);
    }

    private static int[] lineStarts(String content) {
        int[] starts = new int[countLines(content) + 1];
        int idx = 0;
        starts[idx++] = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                starts[idx++] = i + 1;
            }
        }
        return starts;
    }

    private static int countLines(String content) {
        int n = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') n++;
        }
        return n;
    }

    private static int offset(int[] lineStarts, int line, int column) {
        return lineStarts[line - 1] + (column - 1);
    }
}
