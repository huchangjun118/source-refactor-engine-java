package com.github.huchangjun118.sourcerefactorenginejava.engine.tx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.huchangjun118.sourcerefactorenginejava.engine.inc.IncrementalCache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Journals original file bytes before a commit and restores them on
 * rollback. A journal is valid only once its index file is atomically
 * moved into place, so a crash mid-backup cannot create a half journal.
 */
public class JournalManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class JournalEntry {
        private String path;       // project-relative path
        private String backupFile; // file name inside the journal dir

        public JournalEntry() {}

        public JournalEntry(String path, String backupFile) {
            this.path = path;
            this.backupFile = backupFile;
        }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getBackupFile() { return backupFile; }
        public void setBackupFile(String backupFile) { this.backupFile = backupFile; }
    }

    public static class JournalIndex {
        private String id;
        private long createdAtEpochMillis;
        private List<JournalEntry> entries = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }
        public void setCreatedAtEpochMillis(long v) { this.createdAtEpochMillis = v; }
        public List<JournalEntry> getEntries() { return entries; }
        public void setEntries(List<JournalEntry> entries) { this.entries = entries; }
    }

    private static Path journalsDir(Path projectRoot) {
        return projectRoot.resolve(IncrementalCache.STATE_DIR).resolve("journals");
    }

    /** Back up the given files into a new journal; returns the journal id. */
    public String createJournal(Path projectRoot, Map<Path, String> originalContents) {
        String id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path dir = journalsDir(projectRoot).resolve(id);
        try {
            Files.createDirectories(dir);
            JournalIndex index = new JournalIndex();
            index.setId(id);
            index.setCreatedAtEpochMillis(System.currentTimeMillis());
            int i = 0;
            for (Map.Entry<Path, String> e : originalContents.entrySet()) {
                String backupName = "f" + (i++) + ".bak";
                Files.write(dir.resolve(backupName), e.getValue().getBytes(StandardCharsets.UTF_8));
                index.getEntries().add(new JournalEntry(
                        projectRoot.relativize(e.getKey()).toString(), backupName));
            }
            // Index goes in last, atomically: no index => no valid journal.
            Path tmp = dir.resolve("journal.json.tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), index);
            moveAtomic(tmp, dir.resolve("journal.json"));
            return id;
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to create journal in " + dir, ex);
        }
    }

    /** Restore every file recorded in the journal, then delete the journal. */
    public void rollback(Path projectRoot, String journalId) {
        Path dir = journalsDir(projectRoot).resolve(journalId);
        Path indexFile = dir.resolve("journal.json");
        if (!Files.isRegularFile(indexFile)) {
            throw new IllegalArgumentException("no such journal: " + journalId);
        }
        try {
            JournalIndex index = MAPPER.readValue(indexFile.toFile(), JournalIndex.class);
            List<Path> staged = new ArrayList<>();
            List<Path> targets = new ArrayList<>();
            for (JournalEntry entry : index.getEntries()) {
                Path target = projectRoot.resolve(entry.getPath());
                Path tmp = target.resolveSibling(target.getFileName() + ".rollback-tmp");
                Files.copy(dir.resolve(entry.getBackupFile()), tmp,
                        StandardCopyOption.REPLACE_EXISTING);
                staged.add(tmp);
                targets.add(target);
            }
            for (int i = 0; i < staged.size(); i++) {
                moveAtomic(staged.get(i), targets.get(i));
            }
            deleteRecursively(dir);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to roll back journal " + journalId, ex);
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

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
