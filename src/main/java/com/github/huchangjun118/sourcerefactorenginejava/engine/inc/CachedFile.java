package com.github.huchangjun118.sourcerefactorenginejava.engine.inc;

import java.util.ArrayList;
import java.util.List;

/** Cache entry for one source file. */
public class CachedFile {
    private String hash = "";
    private String pkg = "";
    private boolean dependsOnAll;
    private List<String> deps = new ArrayList<>();
    private List<CachedEdit> edits = new ArrayList<>();

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public String getPkg() { return pkg; }
    public void setPkg(String pkg) { this.pkg = pkg; }
    public boolean isDependsOnAll() { return dependsOnAll; }
    public void setDependsOnAll(boolean dependsOnAll) { this.dependsOnAll = dependsOnAll; }
    public List<String> getDeps() { return deps; }
    public void setDeps(List<String> deps) { this.deps = deps; }
    public List<CachedEdit> getEdits() { return edits; }
    public void setEdits(List<CachedEdit> edits) { this.edits = edits; }
}
