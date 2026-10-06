package com.github.huchangjun118.sourcerefactorenginejava.refactor.config;

/** What to do when a candidate reference cannot be resolved symbolically. */
public enum UnresolvedSymbolPolicy {
    /** Leave the occurrence untouched and record a warning in the manifest. */
    SKIP_WITH_WARNING,
    /** Fail the whole task; nothing is written. */
    FAIL
}
