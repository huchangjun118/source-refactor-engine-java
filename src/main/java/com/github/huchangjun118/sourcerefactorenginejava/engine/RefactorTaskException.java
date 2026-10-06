package com.github.huchangjun118.sourcerefactorenginejava.engine;

/** Fatal task failure; when thrown, no file of the project has been modified. */
public class RefactorTaskException extends RuntimeException {
    public RefactorTaskException(String message) {
        super(message);
    }

    public RefactorTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
