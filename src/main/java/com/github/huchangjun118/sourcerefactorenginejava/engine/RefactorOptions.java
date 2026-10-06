package com.github.huchangjun118.sourcerefactorenginejava.engine;

/** Execution options for a refactor task. */
public class RefactorOptions {
    private ParseErrorPolicy parseErrorPolicy = ParseErrorPolicy.SKIP;
    private int concurrency = Math.max(1, Runtime.getRuntime().availableProcessors());
    private long timeoutSeconds = 300;
    private boolean incremental = true;

    public ParseErrorPolicy getParseErrorPolicy() { return parseErrorPolicy; }
    public void setParseErrorPolicy(ParseErrorPolicy parseErrorPolicy) { this.parseErrorPolicy = parseErrorPolicy; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public boolean isIncremental() { return incremental; }
    public void setIncremental(boolean incremental) { this.incremental = incremental; }
}
