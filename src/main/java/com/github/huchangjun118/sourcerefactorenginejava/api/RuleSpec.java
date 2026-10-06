package com.github.huchangjun118.sourcerefactorenginejava.api;

/** JSON description of one rewrite rule. */
public class RuleSpec {
    /** rename-method | rename-field | rename-type */
    private String kind;
    private String id;
    private int order;
    /** rename-method / rename-field: owner type qualified name ("" = any project type). */
    private String ownerType;
    /** rename-method / rename-field. */
    private String oldName;
    /** rename-method / rename-field. */
    private String newName;
    /** rename-type: current qualified name, e.g. "com.acme.OldService". */
    private String qualifiedName;
    /** rename-type: new simple name. */
    private String newSimpleName;

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }
    public String getOldName() { return oldName; }
    public void setOldName(String oldName) { this.oldName = oldName; }
    public String getNewName() { return newName; }
    public void setNewName(String newName) { this.newName = newName; }
    public String getQualifiedName() { return qualifiedName; }
    public void setQualifiedName(String qualifiedName) { this.qualifiedName = qualifiedName; }
    public String getNewSimpleName() { return newSimpleName; }
    public void setNewSimpleName(String newSimpleName) { this.newSimpleName = newSimpleName; }
}
