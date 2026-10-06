package com.github.huchangjun118.sourcerefactorenginejava.api;

import com.github.huchangjun118.sourcerefactorenginejava.engine.ParseErrorPolicy;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorOptions;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorReport;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameFieldRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameMethodRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameTypeRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RewriteRule;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** REST facade over the refactor engine. All processing is local. */
@RestController
@RequestMapping("/api/refactor")
public class RefactorController {
    private final RefactorEngine engine = new RefactorEngine();

    @PostMapping("/preview")
    public RefactorReport preview(@RequestBody RefactorRequest request) {
        return engine.preview(projectDir(request), toRules(request), toOptions(request));
    }

    @PostMapping("/apply")
    public RefactorReport apply(@RequestBody RefactorRequest request) {
        return engine.apply(projectDir(request), toRules(request), toOptions(request));
    }

    @PostMapping("/rollback")
    public Map<String, String> rollback(@RequestBody RollbackRequest request) {
        if (request.getProjectDir() == null || request.getJournalId() == null) {
            throw new IllegalArgumentException("projectDir and journalId are required");
        }
        engine.rollback(Path.of(request.getProjectDir()), request.getJournalId());
        return Map.of("status", "rolled-back", "journalId", request.getJournalId());
    }

    private static Path projectDir(RefactorRequest request) {
        if (request.getProjectDir() == null || request.getProjectDir().isBlank()) {
            throw new IllegalArgumentException("projectDir is required");
        }
        return Path.of(request.getProjectDir());
    }

    static List<RewriteRule> toRules(RefactorRequest request) {
        List<RewriteRule> rules = new ArrayList<>();
        for (RuleSpec spec : request.getRules()) {
            if (spec.getKind() == null || spec.getId() == null) {
                throw new IllegalArgumentException("every rule needs kind and id");
            }
            switch (spec.getKind()) {
                case "rename-method" -> rules.add(new RenameMethodRule(spec.getId(), spec.getOrder(),
                        spec.getOwnerType(), required(spec.getOldName(), "oldName"),
                        required(spec.getNewName(), "newName")));
                case "rename-field" -> rules.add(new RenameFieldRule(spec.getId(), spec.getOrder(),
                        spec.getOwnerType(), required(spec.getOldName(), "oldName"),
                        required(spec.getNewName(), "newName")));
                case "rename-type" -> rules.add(new RenameTypeRule(spec.getId(), spec.getOrder(),
                        required(spec.getQualifiedName(), "qualifiedName"),
                        required(spec.getNewSimpleName(), "newSimpleName")));
                default -> throw new IllegalArgumentException("unknown rule kind: " + spec.getKind());
            }
        }
        return rules;
    }

    static RefactorOptions toOptions(RefactorRequest request) {
        RefactorOptions options = new RefactorOptions();
        if (request.getParseErrorPolicy() != null) {
            options.setParseErrorPolicy(ParseErrorPolicy.valueOf(request.getParseErrorPolicy()));
        }
        if (request.getConcurrency() != null) {
            options.setConcurrency(request.getConcurrency());
        }
        if (request.getTimeoutSeconds() != null) {
            options.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        if (request.getIncremental() != null) {
            options.setIncremental(request.getIncremental());
        }
        return options;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
