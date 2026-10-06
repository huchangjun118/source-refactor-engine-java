package com.github.huchangjun118.sourcerefactorenginejava.engine.rule;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Serializes symbol resolution: JavaParser's symbol solver keeps shared
 * internal caches that are not thread-safe, so all resolve calls go
 * through one global lock. Returns empty on any resolution failure.
 *
 * <p>A thread-local {@link ResolutionListener} can be installed so the
 * incremental engine learns which symbols a file's computation touched.
 */
public final class Resolve {
    private static final Object LOCK = new Object();

    /** Receives the raw resolution result (or failure) of every resolve call. */
    public interface ResolutionListener {
        void onResolved(Object result);
        void onFailure();
    }

    private static final ThreadLocal<ResolutionListener> LISTENER = new ThreadLocal<>();

    private Resolve() {}

    /** Run {@code body} with a listener installed; returns the listener's recordings. */
    public static void withListener(ResolutionListener listener, Runnable body) {
        LISTENER.set(listener);
        try {
            body.run();
        } finally {
            LISTENER.remove();
        }
    }

    public static <T> Optional<T> quietly(Supplier<T> resolution) {
        synchronized (LOCK) {
            ResolutionListener listener = LISTENER.get();
            try {
                T result = resolution.get();
                if (listener != null) {
                    listener.onResolved(result);
                }
                return Optional.ofNullable(result);
            } catch (RuntimeException | StackOverflowError e) {
                if (listener != null) {
                    listener.onFailure();
                }
                return Optional.empty();
            }
        }
    }
}
