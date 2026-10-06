package com.github.huchangjun118.sourcerefactorenginejava.refactor.model;

import java.util.List;

/**
 * A detected conflict between edits (overlapping ranges, or edits from
 * different rules touching the same region). Never silently swallowed:
 * it is either fatal (strategy FAIL) or resolved deterministically
 * (strategy FIRST_WINS) and recorded here.
 */
public record EditConflict(String file, String message, List<String> ruleIds) {
}
