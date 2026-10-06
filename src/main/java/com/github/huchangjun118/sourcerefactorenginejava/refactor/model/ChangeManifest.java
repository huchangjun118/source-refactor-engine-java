package com.github.huchangjun118.sourcerefactorenginejava.refactor.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * The auditable result of a task: every change with file/position/before/after,
 * skipped files, warnings and conflicts. Serialization is deterministic so a
 * replayed task produces a byte-identical manifest.
 */
public class ChangeManifest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private String taskId = "";
    private final List<ChangeEntry> changes = new ArrayList<>();
    private final List<SkippedFile> skippedFiles = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<EditConflict> conflicts = new ArrayList<>();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<ChangeEntry> getChanges() {
        return changes;
    }

    public List<SkippedFile> getSkippedFiles() {
        return skippedFiles;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<EditConflict> getConflicts() {
        return conflicts;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize manifest", e);
        }
    }
}
