package com.github.huchangjun118.sourcerefactorenginejava.refactor.engine;

/** Fatal task error: the task aborts and nothing is written. */
public class RefactorException extends RuntimeException {
    public RefactorException(String message) {
        super(message);
    }

    public RefactorException(String message, Throwable cause) {
        super(message, cause);
    }
}
