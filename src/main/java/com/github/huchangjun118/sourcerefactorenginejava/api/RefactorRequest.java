package com.github.huchangjun118.sourcerefactorenginejava.api;

import java.util.List;

/** JSON request for preview / apply. */
public class RefactorRequest {
    private String projectDir;
    private List<RuleSpec> rules = List.of();
    private String parseErrorPolicy;   // SKIP | FAIL (default SKIP)
    private Integer concurrency;
    private Long timeoutSeconds;
    private Boolean incremental;

    public String getProjectDir() { return projectDir; }
    public void setProjectDir(String projectDir) { this.projectDir = projectDir; }
    public List<RuleSpec> getRules() { return rules; }
    public void setRules(List<RuleSpec> rules) { this.rules = rules; }
    public String getParseErrorPolicy() { return parseErrorPolicy; }
    public void setParseErrorPolicy(String parseErrorPolicy) { this.parseErrorPolicy = parseErrorPolicy; }
    public Integer getConcurrency() { return concurrency; }
    public void setConcurrency(Integer concurrency) { this.concurrency = concurrency; }
    public Long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Boolean getIncremental() { return incremental; }
    public void setIncremental(Boolean incremental) { this.incremental = incremental; }
}
