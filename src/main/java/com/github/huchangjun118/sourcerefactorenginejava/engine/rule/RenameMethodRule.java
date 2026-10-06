package com.github.huchangjun118.sourcerefactorenginejava.engine.rule;

import com.github.javaparser.JavaToken;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.huchangjun118.sourcerefactorenginejava.engine.edit.TextEdit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renames a method and all of its resolved references project-wide.
 * Only methods declared inside the project are renamed; calls resolved to
 * external (e.g. JDK) declarations, string literals, comments and shadowed
 * same-name methods of unrelated types are never touched.
 */
public class RenameMethodRule extends AbstractRule {
    private final String id;
    private final int order;
    private final String ownerType;   // e.g. "com.acme.Service"; "" means any project type
    private final String oldName;
    private final String newName;

    public RenameMethodRule(String id, int order, String ownerType, String oldName, String newName) {
        requireIdentifier(newName, "newName");
        this.id = id;
        this.order = order;
        this.ownerType = ownerType == null ? "" : ownerType;
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override public String id() { return id; }
    @Override public int order() { return order; }
    @Override public String kind() { return "rename-method"; }
    @Override public String fingerprint() {
        return kind() + "|" + id + "|" + order + "|" + ownerType + "|" + oldName + "|" + newName;
    }

    public String getOwnerType() { return ownerType; }
    public String getOldName() { return oldName; }
    public String getNewName() { return newName; }

    @Override
    public List<TextEdit> computeEdits(Path file, CompilationUnit cu, RuleContext context) {
        List<TextEdit> edits = new ArrayList<>();
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            if (!md.getNameAsString().equals(oldName)) {
                continue;
            }
            boolean ownerOk = Resolve.quietly(() -> md.resolve().declaringType().getQualifiedName())
                    .map(qn -> ownerMatches(qn, ownerType))
                    .orElse(false);
            if (ownerOk) {
                md.getName().getRange().ifPresent(range ->
                        edits.add(context.replace(file, range, newName, id, order)));
            }
        }
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals(oldName)) {
                continue;
            }
            if (matchesProjectMethod(Resolve.quietly(call::resolve).orElse(null), context)) {
                call.getName().getRange().ifPresent(range ->
                        edits.add(context.replace(file, range, newName, id, order)));
            }
        }
        for (MethodReferenceExpr ref : cu.findAll(MethodReferenceExpr.class)) {
            if (!ref.getIdentifier().equals(oldName)) {
                continue;
            }
            if (!matchesProjectMethod(Resolve.quietly(ref::resolve).orElse(null), context)) {
                continue;
            }
            // The identifier is the last identifier token of the reference expression.
            ref.getTokenRange().ifPresent(tokenRange -> {
                JavaToken identifierToken = null;
                for (JavaToken token : tokenRange) {
                    if (token.getText().equals(oldName)) {
                        identifierToken = token;
                    }
                }
                if (identifierToken != null && identifierToken.getRange().isPresent()) {
                    edits.add(context.replace(file, identifierToken.getRange().get(), newName, id, order));
                }
            });
        }
        return edits;
    }

    private boolean matchesProjectMethod(ResolvedMethodDeclaration rmd, RuleContext context) {
        if (rmd == null || !rmd.getName().equals(oldName)) {
            return false;
        }
        if (!ownerMatches(rmd.declaringType().getQualifiedName(), ownerType)) {
            return false;
        }
        // The declaration must live in the project, otherwise we cannot
        // rename it consistently (e.g. JDK or library methods).
        return rmd.toAst().map(node -> isProjectNode(node, context)).orElse(false);
    }
}
