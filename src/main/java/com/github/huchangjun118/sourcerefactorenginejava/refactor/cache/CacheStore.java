package com.github.huchangjun118.sourcerefactorenginejava.refactor.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.TextEdit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * Incremental cache. Keys are content/rule fingerprints; values are previously
 * computed declaration lists and per-rule edit lists. All writes are atomic
 * (temp file + move), so a cancelled or timed-out task can never leave a
 * half-written cache entry; corrupt entries are ignored, never fatal.
 */
public class CacheStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dir;
    private final boolean enabled;

    public CacheStore(Path dir, boolean enabled) {
        this.dir = dir;
        this.enabled = enabled;
    }

    public Optional<List<TextEdit>> getEdits(String key) {
        if (!enabled) {
            return Optional.empty();
        }
        return read(fileFor("edits-", key), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    }

    public void putEdits(String key, List<TextEdit> edits) {
        if (enabled) {
            write(fileFor("edits-", key), edits);
        }
    }

    public Optional<List<String>> getDeclarations(String contentHash) {
        if (!enabled) {
            return Optional.empty();
        }
        return read(fileFor("decl-", contentHash), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    }

    public void putDeclarations(String contentHash, List<String> declarations) {
        if (enabled) {
            write(fileFor("decl-", contentHash), declarations);
        }
    }

    private Path fileFor(String prefix, String key) {
        return dir.resolve(prefix + key + ".json");
    }

    private <T> Optional<T> read(Path file, com.fasterxml.jackson.core.type.TypeReference<T> type) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.toFile(), type));
        } catch (Exception corrupt) {
            // A corrupt or half-written entry is treated as a cache miss.
            return Optional.empty();
        }
    }

    private void write(Path file, Object value) {
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "cache-", ".tmp");
            try {
                MAPPER.writeValue(tmp.toFile(), value);
                moveAtomically(tmp, file);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            // Cache failures must never fail the task.
        }
    }

    private static void moveAtomically(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String sha256(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
