package com.github.huchangjun118.sourcerefactorenginejava.engine.rule;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.huchangjun118.sourcerefactorenginejava.engine.edit.TextEdit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renames a field and all resolved references. A local variable or
 * parameter that shadows the field resolves to a different declaration
 * and is therefore left untouched; string literals and comments are never
 * AST identifiers and cannot match.
 */
public class RenameFieldRule extends AbstractRule {
    private final String id;
    private final int order;
    private final String ownerType;
    private final String oldName;
    private final String newName;

    public RenameFieldRule(String id, int order, String ownerType, String oldName, String newName) {
        requireIdentifier(newName, "newName");
        this.id = id;
        this.order = order;
        this.ownerType = ownerType == null ? "" : ownerType;
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override public String id() { return id; }
    @Override public int order() { return order; }
    @Override public String kind() { return "rename-field"; }
    @Override public String fingerprint() {
        return kind() + "|" + id + "|" + order + "|" + ownerType + "|" + oldName + "|" + newName;
    }

    public String getOwnerType() { return ownerType; }
    public String getOldName() { return oldName; }
    public String getNewName() { return newName; }

    @Override
    public List<TextEdit> computeEdits(Path file, CompilationUnit cu, RuleContext context) {
        List<TextEdit> edits = new ArrayList<>();
        for (FieldDeclaration fd : cu.findAll(FieldDeclaration.class)) {
            boolean ownerOk = Resolve.quietly(() -> fd.resolve().declaringType().getQualifiedName())
                    .map(qn -> ownerMatches(qn, ownerType))
                    .orElse(false);
            if (!ownerOk) {
                continue;
            }
            for (VariableDeclarator var : fd.getVariables()) {
                if (var.getNameAsString().equals(oldName) && var.getName().getRange().isPresent()) {
                    edits.add(context.replace(file, var.getName().getRange().get(), newName, id, order));
                }
            }
        }
        for (FieldAccessExpr access : cu.findAll(FieldAccessExpr.class)) {
            if (!access.getNameAsString().equals(oldName)) {
                continue;
            }
            boolean matches = Resolve.quietly(access::resolve)
                    .filter(ResolvedFieldDeclaration.class::isInstance)
                    .map(ResolvedFieldDeclaration.class::cast)
                    .filter(field -> matchesProjectField(field, context))
                    .isPresent();
            if (matches) {
                access.getName().getRange().ifPresent(range ->
                        edits.add(context.replace(file, range, newName, id, order)));
            }
        }
        for (NameExpr name : cu.findAll(NameExpr.class)) {
            if (!name.getNameAsString().equals(oldName)) {
                continue;
            }
            // Only rename when the name resolves to a project field;
            // shadowing locals/params resolve to other declarations.
            boolean matches = Resolve.quietly(name::resolve)
                    .filter(ResolvedFieldDeclaration.class::isInstance)
                    .map(ResolvedFieldDeclaration.class::cast)
                    .filter(field -> matchesProjectField(field, context))
                    .isPresent();
            if (matches) {
                name.getName().getRange().ifPresent(range ->
                        edits.add(context.replace(file, range, newName, id, order)));
            }
        }
        return edits;
    }

    private boolean matchesProjectField(ResolvedFieldDeclaration field, RuleContext context) {
        if (field == null || !field.getName().equals(oldName) || field.toAst().isEmpty()) {
            return false;
        }
        if (!ownerMatches(field.declaringType().getQualifiedName(), ownerType)) {
            return false;
        }
        return field.toAst().map(node -> isProjectNode(node, context)).orElse(false);
    }
}
