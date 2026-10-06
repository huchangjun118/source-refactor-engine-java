package com.github.huchangjun118.sourcerefactorenginejava.refactor.model;

/**
 * A file that was skipped because it could not be parsed. Its content is
 * preserved byte-for-byte. Line/column point at the parse problem (1-based,
 * 0 means unknown).
 */
public record SkippedFile(String file, int line, int column, String reason) {
}
