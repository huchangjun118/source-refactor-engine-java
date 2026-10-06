package com.github.huchangjun118.sourcerefactorenginejava.engine.parse;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.huchangjun118.sourcerefactorenginejava.engine.ParseErrorPolicy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses all Java sources of a project in two phases:
 * 1. plain parse to detect syntax errors and infer source roots from packages;
 * 2. re-parse with a symbol solver bound to the inferred source roots.
 */
public class ProjectParser {

    public ParsedProject parse(Path projectRoot, List<Path> files, ParseErrorPolicy policy) {
        ParsedProject project = new ParsedProject();

        // Phase 1: plain parse, collect issues and source roots.
        JavaParser plain = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        List<Path> goodFiles = new ArrayList<>();
        Set<Path> sourceRoots = new LinkedHashSet<>();
        for (Path file : files) {
            String content;
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
            ParseResult<CompilationUnit> result = plain.parse(content);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                project.getIssues().add(toIssue(file, result));
                continue;
            }
            CompilationUnit cu = result.getResult().get();
            project.getOriginalContents().put(file, content);
            goodFiles.add(file);
            sourceRoots.add(inferSourceRoot(projectRoot, file, cu));
        }

        if (!project.getIssues().isEmpty() && policy == ParseErrorPolicy.FAIL) {
            throw new ProjectParseException(project.getIssues());
        }

        // Phase 2: re-parse with symbol resolution.
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
        for (Path root : sourceRoots) {
            typeSolver.add(new JavaParserTypeSolver(root));
        }
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser resolving = new JavaParser(config);
        for (Path file : goodFiles) {
            String content = project.getOriginalContents().get(file);
            ParseResult<CompilationUnit> result = resolving.parse(content);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                CompilationUnit cu = result.getResult().get();
                cu.setStorage(file);
                project.getUnits().put(file, cu);
            } else {
                // Should not happen (phase 1 succeeded); treat as skip to stay safe.
                project.getIssues().add(toIssue(file, result));
                project.getOriginalContents().remove(file);
            }
        }
        return project;
    }

    private static ParseIssue toIssue(Path file, ParseResult<CompilationUnit> result) {
        if (!result.getProblems().isEmpty()) {
            Problem p = result.getProblems().get(0);
            Integer line = null;
            Integer column = null;
            if (p.getLocation().isPresent()
                    && p.getLocation().get().getBegin().getRange().isPresent()) {
                var pos = p.getLocation().get().getBegin().getRange().get().begin;
                line = pos.line;
                column = pos.column;
            }
            return new ParseIssue(file.toString(), p.getMessage(), line, column);
        }
        return new ParseIssue(file.toString(), "unparseable file", null, null);
    }

    /** Infer the source root by stripping the declared package path from the file path. */
    static Path inferSourceRoot(Path projectRoot, Path file, CompilationUnit cu) {
        Path dir = file.getParent();
        if (cu.getPackageDeclaration().isPresent()) {
            String pkg = cu.getPackageDeclaration().get().getNameAsString();
            String[] parts = pkg.split("\\.");
            Path candidate = dir;
            boolean matches = true;
            for (int i = parts.length - 1; i >= 0; i--) {
                if (candidate == null || !candidate.getFileName().toString().equals(parts[i])) {
                    matches = false;
                    break;
                }
                candidate = candidate.getParent();
            }
            if (matches && candidate != null) {
                return candidate;
            }
        }
        return dir == null ? projectRoot : dir;
    }
}
