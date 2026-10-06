package com.github.huchangjun118.sourcerefactorenginejava.refactor.engine;

/** The task was cancelled (interrupted) or hit its deadline before committing. */
public class TaskCancelledException extends RefactorException {
    public TaskCancelledException(String message) {
        super(message);
    }
}
