package com.github.huchangjun118.sourcerefactorenginejava.refactor.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration of a refactor task.
 */
public class RefactorConfig {

    private Path sourceDir;
    private List<RuleConfig> rules = new ArrayList<>();
    private ParseErrorPolicy onParseError = ParseErrorPolicy.SKIP;
    private UnresolvedSymbolPolicy onUnresolved = UnresolvedSymbolPolicy.SKIP_WITH_WARNING;
    private ConflictStrategy conflictStrategy = ConflictStrategy.FAIL;
    private int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
    private long timeoutMillis = 0;
    private boolean cacheEnabled = true;
    /** Directory for cache/journal/work state. Defaults to {@code <sourceDir>/.refactor}. */
    private Path stateDir;

    public static RefactorConfig load(Path jsonFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonFile.toFile(), RefactorConfig.class);
    }

    public Path getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(Path sourceDir) {
        this.sourceDir = sourceDir;
    }

    public List<RuleConfig> getRules() {
        return rules;
    }

    public void setRules(List<RuleConfig> rules) {
        this.rules = rules;
    }

    public ParseErrorPolicy getOnParseError() {
        return onParseError;
    }

    public void setOnParseError(ParseErrorPolicy onParseError) {
        this.onParseError = onParseError;
    }

    public UnresolvedSymbolPolicy getOnUnresolved() {
        return onUnresolved;
    }

    public void setOnUnresolved(UnresolvedSymbolPolicy onUnresolved) {
        this.onUnresolved = onUnresolved;
    }

    public ConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setConflictStrategy(ConflictStrategy conflictStrategy) {
        this.conflictStrategy = conflictStrategy;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Path getStateDir() {
        return stateDir != null ? stateDir : sourceDir.resolve(".refactor");
    }

    public void setStateDir(Path stateDir) {
        this.stateDir = stateDir;
    }

    public Path cacheDir() {
        return getStateDir().resolve("cache");
    }

    public Path journalDir() {
        return getStateDir().resolve("journal");
    }

    public Path workDir() {
        return getStateDir().resolve("work");
    }
}
