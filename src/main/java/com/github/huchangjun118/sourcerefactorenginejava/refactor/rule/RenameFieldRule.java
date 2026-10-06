package com.github.huchangjun118.sourcerefactorenginejava.refactor.rule;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.TextEdit;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.parse.ParsedFile;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * Renames a field of a given owner type and updates all access sites that
 * resolve to that field. Local variables and parameters that shadow the field
 * name resolve to themselves and are therefore left untouched, as are string
 * literals and comments.
 */
public class RenameFieldRule implements RewriteRule {

    private final String id;
    private final int order;
    private final String owner;
    private final String oldName;
    private final String newName;

    public RenameFieldRule(String id, int order, String owner, String oldName, String newName) {
        this.id = id;
        this.order = order;
        this.owner = owner;
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public List<TextEdit> collectEdits(ParsedFile file, RuleContext ctx) {
        List<TextEdit> edits = new ArrayList<>();

        // 1. The declaration itself (only inside the owner type).
        for (FieldDeclaration fd : file.cu().findAll(FieldDeclaration.class)) {
            boolean inOwner = TypeNames.enclosingTypeFqn(fd)
                    .map(fqn -> owner.equals(fqn))
                    .orElse(false);
            if (!inOwner) {
                continue;
            }
            for (VariableDeclarator var : fd.getVariables()) {
                if (var.getNameAsString().equals(oldName)) {
                    edits.add(file.edit(var.getName().getRange()
                            .orElseThrow(() -> new IllegalStateException("node without range")), newName, id));
                }
            }
        }

        // 2. Explicit field accesses such as this.legacy or service.legacy.
        for (FieldAccessExpr access : file.cu().findAll(FieldAccessExpr.class)) {
            if (!access.getNameAsString().equals(oldName)) {
                continue;
            }
            try {
                ResolvedValueDeclaration resolved = access.resolve();
                if (isTargetField(resolved)) {
                    edits.add(file.edit(access.getName().getRange()
                            .orElseThrow(() -> new IllegalStateException("node without range")), newName, id));
                }
            } catch (Throwable t) {
                ctx.unresolved("field access '." + oldName + "'", access.getName().getRange());
            }
        }

        // 3. Plain name expressions (unqualified reads/writes of the field).
        for (NameExpr name : file.cu().findAll(NameExpr.class)) {
            if (!name.getNameAsString().equals(oldName)) {
                continue;
            }
            try {
                ResolvedValueDeclaration resolved = name.resolve();
                if (isTargetField(resolved)) {
                    edits.add(file.edit(name.getRange()
                            .orElseThrow(() -> new IllegalStateException("node without range")), newName, id));
                }
                // Local variables / parameters shadowing the field resolve to
                // themselves, not to the field, and are skipped here.
            } catch (Throwable t) {
                ctx.unresolved("name '" + oldName + "'", name.getRange());
            }
        }

        return edits;
    }

    private boolean isTargetField(ResolvedValueDeclaration resolved) {
        if (!(resolved instanceof ResolvedFieldDeclaration field)) {
            return false;
        }
        return field.declaringType().getQualifiedName().equals(owner);
    }

}
