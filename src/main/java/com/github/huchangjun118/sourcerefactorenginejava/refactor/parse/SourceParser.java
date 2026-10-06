package com.github.huchangjun118.sourcerefactorenginejava.refactor.parse;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Parses Java sources. Each parse uses a fresh parser (and symbol solver)
 * instance so parsing is thread-safe and never serves stale state.
 */
public class SourceParser {

    private final Path symbolRoot;

    /** @param symbolRoot directory used by the symbol solver to resolve project types; may be null. */
    public SourceParser(Path symbolRoot) {
        this.symbolRoot = symbolRoot;
    }

    public ParseOutcome parse(Path path, String relPath, String content) {
        ParserConfiguration config = new ParserConfiguration()
                .setCharacterEncoding(StandardCharsets.UTF_8)
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        if (symbolRoot != null) {
            CombinedTypeSolver typeSolver = new CombinedTypeSolver(
                    new ReflectionTypeSolver(false),
                    new JavaParserTypeSolver(symbolRoot));
            config.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        }
        try {
            ParseResult<CompilationUnit> result = new JavaParser(config).parse(content);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                return new ParseOutcome.Ok(new ParsedFile(path, relPath, content, result.getResult().get()));
            }
            return error(relPath, result.getProblems().stream().findFirst()
                    .map(p -> p).orElse(null), "parse failed");
        } catch (ParseProblemException e) {
            return error(relPath, e.getProblems().stream().findFirst().orElse(null), "parse failed");
        } catch (Exception e) {
            return new ParseOutcome.Err(relPath, 0, 0, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private ParseOutcome error(String relPath, Problem problem, String fallback) {
        if (problem == null) {
            return new ParseOutcome.Err(relPath, 0, 0, fallback);
        }
        int line = problem.getLocation()
                .flatMap(l -> l.getBegin().getRange().map(r -> r.begin.line)).orElse(0);
        int column = problem.getLocation()
                .flatMap(l -> l.getBegin().getRange().map(r -> r.begin.column)).orElse(0);
        String message = problem.getVerboseMessage();
        return new ParseOutcome.Err(relPath, line, column, message == null ? fallback : message);
    }
}
