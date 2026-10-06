package com.github.huchangjun118.sourcerefactorenginejava.refactor.commit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Atomically commits a set of file changes.
 *
 * <p>Protocol: (1) write the journal index, (2) back up every original file
 * into the per-task journal, (3) overwrite each target via temp-file + atomic
 * move. Any failure during step 3 restores the already-written files from the
 * journal, so the working tree never ends up half new / half old. A committed
 * task can later be rolled back as a whole from its journal.
 */
public class FileCommitter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INDEX = "journal.json";
    private static final String FILES = "files";
    private static final String COMMITTED = "committed.marker";
    private static final String ROLLED_BACK = "rolledback.marker";

    /**
     * Commits all changes or none.
     *
     * @param root       project root the relative paths are resolved against
     * @param changes    relative path -> new content
     * @param journalDir directory holding per-task journals
     * @param taskId     id of the task being committed
     */
    public void commit(Path root, Map<String, String> changes, Path journalDir, String taskId) {
        if (changes.isEmpty()) {
            return;
        }
        Map<String, String> sorted = new TreeMap<>(changes);
        Path journal = journalDir.resolve(taskId);
        try {
            Files.createDirectories(journal.resolve(FILES));
            // Re-running a byte-identical task must not clobber the original backups.
            boolean freshJournal = !Files.exists(journal.resolve(INDEX));
            if (freshJournal) {
                writeJournalIndex(journal, new ArrayList<>(sorted.keySet()));
                for (String relPath : sorted.keySet()) {
                    Path original = root.resolve(relPath);
                    Path backup = backupPath(journal, relPath);
                    Files.createDirectories(backup.getParent());
                    Files.copy(original, backup, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException e) {
            throw new RefactorException("failed to prepare commit journal for task " + taskId, e);
        }

        List<String> written = new ArrayList<>();
        try {
            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                Path target = root.resolve(entry.getKey());
                hookBeforeWrite(target);
                writeAtomically(target, entry.getValue());
                written.add(entry.getKey());
            }
        } catch (IOException | RuntimeException e) {
            // Roll back everything written so far: all-or-nothing.
            List<String> restoreErrors = new ArrayList<>();
            for (String relPath : written) {
                try {
                    Files.copy(backupPath(journal, relPath), root.resolve(relPath),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException restoreFailure) {
                    restoreErrors.add(relPath + ": " + restoreFailure.getMessage());
                }
            }
            String message = "commit failed for task " + taskId + ", rolled back " + written.size()
                    + " file(s): " + e.getMessage();
            if (!restoreErrors.isEmpty()) {
                message += "; RESTORE FAILURES: " + restoreErrors;
            }
            throw new RefactorException(message, e);
        }

        try {
            Files.writeString(journal.resolve(COMMITTED), "committed\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Restores every file of a committed task to its pre-task content. */
    public void rollback(Path root, Path journalDir, String taskId) {
        Path journal = journalDir.resolve(taskId);
        Path index = journal.resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            throw new RefactorException("no journal found for task " + taskId);
        }
        List<String> files;
        try {
            files = MAPPER.readValue(index.toFile(),
                    MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException e) {
            throw new RefactorException("cannot read journal for task " + taskId, e);
        }
        List<String> errors = new ArrayList<>();
        for (String relPath : files) {
            Path backup = backupPath(journal, relPath);
            try {
                writeAtomically(root.resolve(relPath),
                        Files.readString(backup, StandardCharsets.UTF_8));
            } catch (IOException e) {
                errors.add(relPath + ": " + e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new RefactorException("rollback of task " + taskId + " failed for: " + errors);
        }
        try {
            Files.writeString(journal.resolve(ROLLED_BACK), "rolled back\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Test hook: invoked before each file write. Default does nothing. */
    protected void hookBeforeWrite(Path target) throws IOException {
    }

    /** Writes content to target via a temp file in the same directory + atomic move. */
    protected void writeAtomically(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = Files.createTempFile(parent != null ? parent : target.toAbsolutePath().getParent(),
                ".refactor-", ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void writeJournalIndex(Path journal, List<String> files) throws IOException {
        Path tmp = journal.resolve(INDEX + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), files);
        try {
            Files.move(tmp, journal.resolve(INDEX),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, journal.resolve(INDEX), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path backupPath(Path journal, String relPath) {
        return journal.resolve(FILES).resolve(relPath);
    }
}
