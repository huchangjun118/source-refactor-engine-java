package com.github.huchangjun118.sourcerefactorenginejava.engine.tx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntConsumer;

/**
 * All-or-nothing writer. New contents are staged to sibling temp files
 * first; only when every file is staged does the move phase begin. If any
 * move fails, every file touched so far is restored from in-memory
 * originals, so the project never stays half-new/half-old.
 */
public class FileCommitter {
    private final IntConsumer afterMoveHook; // test hook, may be null

    public FileCommitter() {
        this(null);
    }

    /** Visible for tests: hook invoked with the move index after each move. */
    public FileCommitter(IntConsumer afterMoveHook) {
        this.afterMoveHook = afterMoveHook;
    }

    public void commit(Map<Path, String> newContents) {
        // Deterministic order.
        Map<Path, String> sorted = new TreeMap<>(newContents);
        Map<Path, byte[]> originals = new java.util.HashMap<>();
        List<Path> staged = new ArrayList<>();
        List<Path> targets = new ArrayList<>();
        try {
            for (Map.Entry<Path, String> e : sorted.entrySet()) {
                originals.put(e.getKey(), Files.readAllBytes(e.getKey()));
            }
            for (Map.Entry<Path, String> e : sorted.entrySet()) {
                Path tmp = e.getKey().resolveSibling(
                        e.getKey().getFileName() + ".refactor-tmp");
                Files.write(tmp, e.getValue().getBytes(StandardCharsets.UTF_8));
                staged.add(tmp);
                targets.add(e.getKey());
            }
            List<Path> moved = new ArrayList<>();
            for (int i = 0; i < staged.size(); i++) {
                moveAtomic(staged.get(i), targets.get(i));
                moved.add(targets.get(i));
                if (afterMoveHook != null) {
                    afterMoveHook.accept(i);
                }
            }
        } catch (IOException | RuntimeException ex) {
            // Restore every file we may have touched; clean up staged temps.
            for (Map.Entry<Path, byte[]> e : originals.entrySet()) {
                try {
                    Files.write(e.getKey(), e.getValue());
                } catch (IOException io) {
                    ex.addSuppressed(io);
                }
            }
            for (Path tmp : staged) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException io) {
                    ex.addSuppressed(io);
                }
            }
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new UncheckedIOException("commit failed; all files restored", (IOException) ex);
        }
    }

    private static void moveAtomic(Path src, Path dst) throws IOException {
        try {
            Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
