package com.github.huchangjun118.sourcerefactorenginejava.refactor.rule;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.TextEdit;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.parse.ParsedFile;

import java.util.List;

/**
 * A symbol-level rewrite rule. Rules are applied in passes ordered by
 * {@link #order()} (ties broken by {@link #id()}); each pass sees the result
 * of the previous passes.
 */
public interface RewriteRule {

    String id();

    int order();

    /**
     * Collect the edits this rule makes in one parsed file. Must be
     * deterministic and must not mutate the AST or any shared state other
     * than through the given context.
     */
    List<TextEdit> collectEdits(ParsedFile file, RuleContext ctx);
}
