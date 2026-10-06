package com.github.huchangjun118.sourcerefactorenginejava.engine.rule;

import com.github.javaparser.ast.CompilationUnit;
import com.github.huchangjun118.sourcerefactorenginejava.engine.edit.TextEdit;

import java.nio.file.Path;
import java.util.List;

/** A single rewrite rule producing text edits for one file at a time. */
public interface RewriteRule {
    /** Unique, stable rule id. */
    String id();

    /** Deterministic ordering key; lower runs first and wins conflicts. */
    int order();

    /** Rule kind used for (de)serialization and rule-set hashing. */
    String kind();

    /** Stable parameter string used to hash the rule set. */
    String fingerprint();

    /** Compute edits for one parsed file. Must be deterministic. */
    List<TextEdit> computeEdits(Path file, CompilationUnit unit, RuleContext context);
}
