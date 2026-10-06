package com.github.huchangjun118.sourcerefactorenginejava.refactor.rule;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RuleConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Instantiates and validates rules from configuration. */
public final class RuleFactory {

    private RuleFactory() {
    }

    public static RewriteRule create(RuleConfig config) {
        Map<String, String> p = config.getParams();
        String id = config.getId();
        if (id == null || id.isBlank()) {
            throw new RefactorException("rule without id");
        }
        if (config.getType() == null) {
            throw new RefactorException("rule " + id + " without type");
        }
        return switch (config.getType()) {
            case "rename-method" -> new RenameMethodRule(id, config.getOrder(),
                    required(p, "owner", id), required(p, "oldName", id), required(p, "newName", id));
            case "rename-field" -> new RenameFieldRule(id, config.getOrder(),
                    required(p, "owner", id), required(p, "oldName", id), required(p, "newName", id));
            case "rename-type" -> new RenameTypeRule(id, config.getOrder(),
                    required(p, "oldFqn", id), required(p, "newName", id));
            default -> throw new RefactorException(
                    "unknown rule type '" + config.getType() + "' for rule " + id);
        };
    }

    /**
     * Creates all rules and sorts them into deterministic application order:
     * by {@code order}, ties broken by rule id (a warning is emitted for ties).
     */
    public static List<RewriteRule> createAll(List<RuleConfig> configs, List<String> warnings) {
        Set<String> ids = new HashSet<>();
        List<RewriteRule> rules = new ArrayList<>();
        for (RuleConfig config : configs) {
            RewriteRule rule = create(config);
            if (!ids.add(rule.id())) {
                throw new RefactorException("duplicate rule id: " + rule.id());
            }
            rules.add(rule);
        }
        rules.sort(Comparator.comparingInt(RewriteRule::order).thenComparing(RewriteRule::id));
        for (int i = 1; i < rules.size(); i++) {
            if (rules.get(i).order() == rules.get(i - 1).order()) {
                warnings.add("rules '" + rules.get(i - 1).id() + "' and '" + rules.get(i).id()
                        + "' share order " + rules.get(i).order()
                        + "; applied in id order (deterministic)");
            }
        }
        return rules;
    }

    private static String required(Map<String, String> params, String key, String ruleId) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new RefactorException("rule " + ruleId + " misses required param '" + key + "'");
        }
        return value;
    }
}
