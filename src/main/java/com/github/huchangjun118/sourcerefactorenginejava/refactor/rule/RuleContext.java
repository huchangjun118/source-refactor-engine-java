package com.github.huchangjun118.sourcerefactorenginejava.refactor.rule;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.UnresolvedSymbolPolicy;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorException;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.parse.ParsedFile;
import com.github.javaparser.Range;

import java.util.List;
import java.util.Optional;

/**
 * Per-file, per-pass context handed to rules: access to the parsed file,
 * a warning sink and the unresolved-symbol policy.
 */
public class RuleContext {

    private final ParsedFile file;
    private final UnresolvedSymbolPolicy policy;
    private final List<String> warnings;

    public RuleContext(ParsedFile file, UnresolvedSymbolPolicy policy, List<String> warnings) {
        this.file = file;
        this.policy = policy;
        this.warnings = warnings;
    }

    public ParsedFile file() {
        return file;
    }

    /** A candidate reference could not be resolved symbolically. */
    public void unresolved(String what, Optional<Range> range) {
        String pos = range.map(r -> r.begin.line + ":" + r.begin.column).orElse("?:?");
        String message = file.relPath() + ":" + pos + " unresolved symbol: " + what;
        if (policy == UnresolvedSymbolPolicy.FAIL) {
            throw new RefactorException(message);
        }
        warnings.add(message);
    }

    public void warn(String message) {
        warnings.add(file.relPath() + " " + message);
    }

    public List<String> warnings() {
        return warnings;
    }
}
