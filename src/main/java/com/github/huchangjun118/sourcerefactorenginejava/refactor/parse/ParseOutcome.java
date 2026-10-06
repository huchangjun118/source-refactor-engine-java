package com.github.huchangjun118.sourcerefactorenginejava.refactor.parse;

/** Result of parsing one file: either the parsed file or a located error. */
public sealed interface ParseOutcome {

    record Ok(ParsedFile file) implements ParseOutcome {
    }

    record Err(String relPath, int line, int column, String message) implements ParseOutcome {
    }
}
