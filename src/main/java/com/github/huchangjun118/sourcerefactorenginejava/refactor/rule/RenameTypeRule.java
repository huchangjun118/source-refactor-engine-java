package com.github.huchangjun118.sourcerefactorenginejava.refactor.rule;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.TextEdit;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.parse.ParsedFile;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.ArrayList;
import java.util.List;

/**
 * Renames the simple name of a type (package unchanged) and updates imports,
 * constructor declarations and all type references that resolve to it.
 * The file on disk is not renamed.
 */
public class RenameTypeRule implements RewriteRule {

    private final String id;
    private final int order;
    private final String oldFqn;
    private final String newName;

    public RenameTypeRule(String id, int order, String oldFqn, String newName) {
        this.id = id;
        this.order = order;
        this.oldFqn = oldFqn;
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
        String oldSimpleName = oldFqn.substring(oldFqn.lastIndexOf('.') + 1);
        String packageName = oldFqn.substring(0, oldFqn.lastIndexOf('.') + 1); // includes trailing dot
        List<TextEdit> edits = new ArrayList<>();

        // 1. The type declaration itself.
        for (TypeDeclaration<?> td : file.cu().findAll(TypeDeclaration.class)) {
            if (!td.getNameAsString().equals(oldSimpleName)) {
                continue;
            }
            boolean isTarget = td.getFullyQualifiedName().map(oldFqn::equals).orElse(false);
            if (isTarget) {
                edits.add(file.edit(td.getName().getRange()
                        .orElseThrow(() -> new IllegalStateException("node without range")), newName, id));
            }
        }

        // 2. Constructor declarations of the target type.
        for (ConstructorDeclaration cd : file.cu().findAll(ConstructorDeclaration.class)) {
            if (!cd.getNameAsString().equals(oldSimpleName)) {
                continue;
            }
            boolean inTarget = TypeNames.enclosingTypeFqn(cd).map(oldFqn::equals).orElse(false);
            if (inTarget) {
                edits.add(file.edit(cd.getName().getRange()
                        .orElseThrow(() -> new IllegalStateException("node without range")), newName, id));
            }
        }

        // 3. Import statements pointing at the old type.
        for (ImportDeclaration imp : file.cu().findAll(ImportDeclaration.class)) {
            if (imp.isAsterisk()) {
                continue;
            }
            String imported = imp.getNameAsString();
            // static imports of members are left untouched
            if (!imp.isStatic() && imported.equals(oldFqn)) {
                edits.add(file.edit(imp.getName().getRange()
                        .orElseThrow(() -> new IllegalStateException("node without range")),
                        packageName + newName, id));
            }
        }

        // 4. Type references resolved through the symbol solver.
        for (ClassOrInterfaceType type : file.cu().findAll(ClassOrInterfaceType.class)) {
            if (!type.getNameAsString().equals(oldSimpleName)) {
                continue;
            }
            try {
                ResolvedType resolved = type.resolve();
                if (resolved.isReferenceType()
                        && matches(resolved.asReferenceType(), oldFqn)) {
                    edits.add(file.edit(type.getName().getRange()
                            .orElseThrow(() -> new IllegalStateException("node without range")), newName, id));
                }
            } catch (Throwable t) {
                ctx.unresolved("type reference '" + oldSimpleName + "'", type.getName().getRange());
            }
        }

        return edits;
    }

    private boolean matches(ResolvedReferenceType type, String fqn) {
        if (type.getQualifiedName().equals(fqn)) {
            return true;
        }
        // generic type whose raw type matches
        return type.isReferenceType() && type.asReferenceType().describe().equals(fqn);
    }
}
