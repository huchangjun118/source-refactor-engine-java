package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RuleConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Helpers to build throwaway source projects and configs for tests. */
final class TestSupport {

    private TestSupport() {
    }

    static Path writeProject(Path dir, Map<String, String> files) {
        files.forEach((relPath, content) -> {
            try {
                Path target = dir.resolve(relPath);
                Files.createDirectories(target.getParent());
                Files.writeString(target, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return dir;
    }

    static RefactorConfig config(Path sourceDir, RuleConfig... rules) {
        RefactorConfig config = new RefactorConfig();
        config.setSourceDir(sourceDir);
        config.setRules(List.of(rules));
        return config;
    }

    static RuleConfig renameMethod(String id, int order, String owner, String oldName, String newName) {
        RuleConfig rule = new RuleConfig();
        rule.setId(id);
        rule.setType("rename-method");
        rule.setOrder(order);
        rule.setParams(Map.of("owner", owner, "oldName", oldName, "newName", newName));
        return rule;
    }

    static RuleConfig renameField(String id, int order, String owner, String oldName, String newName) {
        RuleConfig rule = new RuleConfig();
        rule.setId(id);
        rule.setType("rename-field");
        rule.setOrder(order);
        rule.setParams(Map.of("owner", owner, "oldName", oldName, "newName", newName));
        return rule;
    }

    static RuleConfig renameType(String id, int order, String oldFqn, String newName) {
        RuleConfig rule = new RuleConfig();
        rule.setId(id);
        rule.setType("rename-type");
        rule.setOrder(order);
        rule.setParams(Map.of("oldFqn", oldFqn, "newName", newName));
        return rule;
    }

    static String read(Path dir, String relPath) {
        try {
            return Files.readString(dir.resolve(relPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
