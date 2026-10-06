package com.github.huchangjun118.sourcerefactorenginejava.refactor.config;

/** What to do with files that cannot be parsed. */
public enum ParseErrorPolicy {
    /** Skip the file (recorded in the manifest), preserve its content byte-for-byte. */
    SKIP,
    /** Fail the whole task; nothing is written. */
    FAIL
}
