package com.github.huchangjun118.sourcerefactorenginejava.engine.parse;

import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Result of parsing a project: parsed units + per-file issues. */
public class ParsedProject {
    private final Map<Path, CompilationUnit> units = new LinkedHashMap<>();
    private final Map<Path, String> originalContents = new LinkedHashMap<>();
    private final List<ParseIssue> issues = new java.util.ArrayList<>();

    public Map<Path, CompilationUnit> getUnits() { return units; }
    public Map<Path, String> getOriginalContents() { return originalContents; }
    public List<ParseIssue> getIssues() { return issues; }
}
