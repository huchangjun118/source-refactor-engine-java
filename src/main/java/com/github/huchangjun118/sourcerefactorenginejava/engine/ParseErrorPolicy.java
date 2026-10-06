package com.github.huchangjun118.sourcerefactorenginejava.engine;

/** Policy for files that cannot be parsed. */
public enum ParseErrorPolicy {
    /** Skip the broken file, keep its original content, record the issue. */
    SKIP,
    /** Fail the whole task; nothing is written. */
    FAIL
}
