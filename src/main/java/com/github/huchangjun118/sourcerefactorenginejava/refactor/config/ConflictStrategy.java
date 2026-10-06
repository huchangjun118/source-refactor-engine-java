package com.github.huchangjun118.sourcerefactorenginejava.refactor.config;

/** How to handle conflicting edits. */
public enum ConflictStrategy {
    /** Abort the task; nothing is written. */
    FAIL,
    /** The edit produced by the earlier rule (lower order, then id) wins; the loser is dropped and recorded. */
    FIRST_WINS
}
