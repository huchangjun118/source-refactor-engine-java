package com.github.huchangjun118.sourcerefactorenginejava.engine.inc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Root object of the on-disk incremental cache. */
public class CacheData {
    private String ruleSetHash = "";
    private Map<String, CachedFile> files = new LinkedHashMap<>();
    /** package name -> sorted list of member file paths (for add/remove invalidation). */
    private Map<String, List<String>> packageMembers = new LinkedHashMap<>();

    public String getRuleSetHash() { return ruleSetHash; }
    public void setRuleSetHash(String ruleSetHash) { this.ruleSetHash = ruleSetHash; }
    public Map<String, CachedFile> getFiles() { return files; }
    public void setFiles(Map<String, CachedFile> files) { this.files = files; }
    public Map<String, List<String>> getPackageMembers() { return packageMembers; }
    public void setPackageMembers(Map<String, List<String>> packageMembers) { this.packageMembers = packageMembers; }
}
