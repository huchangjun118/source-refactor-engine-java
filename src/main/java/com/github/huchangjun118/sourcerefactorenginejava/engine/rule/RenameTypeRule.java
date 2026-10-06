package com.github.huchangjun118.sourcerefactorenginejava.engine.rule;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.huchangjun118.sourcerefactorenginejava.engine.edit.TextEdit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Renames a type (class / interface / enum / record / annotation) and all
 * resolved references: declarations, constructors, type usages, annotations,
 * imports and static member accesses through the type name.
 */
public class RenameTypeRule extends AbstractRule {
    private final String id;
    private final int order;
    private final String qualifiedName; // e.g. "com.acme.OldService"
    private final String newSimpleName;
    private final String oldSimpleName;

    public RenameTypeRule(String id, int order, String qualifiedName, String newSimpleName) {
        requireIdentifier(newSimpleName, "newSimpleName");
        this.id = id;
        this.order = order;
        this.qualifiedName = qualifiedName;
        this.newSimpleName = newSimpleName;
        int dot = qualifiedName.lastIndexOf('.');
        this.oldSimpleName = dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    @Override public String id() { return id; }
    @Override public int order() { return order; }
    @Override public String kind() { return "rename-type"; }
    @Override public String fingerprint() {
        return kind() + "|" + id + "|" + order + "|" + qualifiedName + "|" + newSimpleName;
    }

    public String getQualifiedName() { return qualifiedName; }
    public String getNewSimpleName() { return newSimpleName; }

    @Override
    public List<TextEdit> computeEdits(Path file, CompilationUnit cu, RuleContext context) {
        List<TextEdit> edits = new ArrayList<>();
        renameTypeDeclarations(file, cu, context, edits);
        renameConstructors(file, cu, context, edits);
        renameTypeUsages(file, cu, context, edits);
        renameAnnotations(file, cu, context, edits);
        renameImports(file, cu, context, edits);
        renameStaticAccesses(file, cu, context, edits);
        return edits;
    }

    private void renameSimpleName(Path file, RuleContext context, List<TextEdit> edits,
                                  NodeWithSimpleName<?> node) {
        node.getName().getRange().ifPresent(range ->
                edits.add(context.replace(file, range, newSimpleName, id, order)));
    }

    private boolean resolvesToTarget(ClassOrInterfaceType type) {
        return Resolve.quietly(type::resolve)
                .map(rt -> rt.isReferenceType()
                        && qualifiedName.equals(rt.asReferenceType().getQualifiedName()))
                .orElse(false);
    }

    private void renameTypeDeclarations(Path file, CompilationUnit cu, RuleContext context,
                                        List<TextEdit> edits) {
        for (TypeDeclaration<?> td : cu.findAll(TypeDeclaration.class)) {
            if (!td.getNameAsString().equals(oldSimpleName)) {
                continue;
            }
            boolean matches = Resolve.quietly(() -> td.resolve().getQualifiedName())
                    .map(qualifiedName::equals).orElse(false);
            if (matches) {
                renameSimpleName(file, context, edits, td);
            }
        }
    }

    private void renameConstructors(Path file, CompilationUnit cu, RuleContext context,
                                    List<TextEdit> edits) {
        for (ConstructorDeclaration cd : cu.findAll(ConstructorDeclaration.class)) {
            if (!cd.getNameAsString().equals(oldSimpleName)) {
                continue;
            }
            Optional<TypeDeclaration> parent = cd.findAncestor(TypeDeclaration.class);
            if (parent.isEmpty()) {
                continue;
            }
            boolean matches = Resolve.quietly(() -> parent.get().resolve().getQualifiedName())
                    .map(qualifiedName::equals).orElse(false);
            if (matches) {
                renameSimpleName(file, context, edits, cd);
            }
        }
    }

    private void renameTypeUsages(Path file, CompilationUnit cu, RuleContext context,
                                  List<TextEdit> edits) {
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (!type.getNameAsString().equals(oldSimpleName)) {
                continue;
            }
            if (resolvesToTarget(type)) {
                renameSimpleName(file, context, edits, type);
            }
        }
    }

    private void renameAnnotations(Path file, CompilationUnit cu, RuleContext context,
                                   List<TextEdit> edits) {
        for (AnnotationExpr annotation : cu.findAll(AnnotationExpr.class)) {
            if (!annotation.getName().getIdentifier().equals(oldSimpleName)) {
                continue;
            }
            boolean matches = Resolve.quietly(() -> annotation.resolve().getQualifiedName())
                    .map(qualifiedName::equals).orElse(false);
            if (matches) {
                annotation.getName().getTokenRange().ifPresent(tokenRange -> {
                    com.github.javaparser.JavaToken last = null;
                    for (com.github.javaparser.JavaToken token : tokenRange) {
                        last = token;
                    }
                    if (last != null && last.getText().equals(oldSimpleName)
                            && last.getRange().isPresent()) {
                        edits.add(context.replace(file, last.getRange().get(),
                                newSimpleName, id, order));
                    }
                });
            }
        }
    }

    private void renameImports(Path file, CompilationUnit cu, RuleContext context,
                               List<TextEdit> edits) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk()) {
                continue;
            }
            // Walk the qualified name chain; find the segment whose cumulative
            // text equals the target qualified name (covers plain imports and
            // the type part of static imports).
            Name current = imp.getName();
            while (current != null) {
                if (current.asString().equals(qualifiedName)
                        && current.getIdentifier().equals(oldSimpleName)) {
                    // Replace only the identifier token, not the whole qualifier chain.
                    current.getTokenRange().ifPresent(tokenRange -> {
                        com.github.javaparser.JavaToken last = null;
                        for (com.github.javaparser.JavaToken token : tokenRange) {
                            last = token;
                        }
                        if (last != null && last.getText().equals(oldSimpleName)
                                && last.getRange().isPresent()) {
                            edits.add(context.replace(file, last.getRange().get(),
                                    newSimpleName, id, order));
                        }
                    });
                    break;
                }
                current = current.getQualifier().orElse(null);
            }
        }
    }

    /** Static accesses like {@code OldService.staticCall()} or {@code OldService.CONST}. */
    private void renameStaticAccesses(Path file, CompilationUnit cu, RuleContext context,
                                      List<TextEdit> edits) {
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            call.getScope().ifPresent(scope -> renameTypeScope(file, context, edits, scope,
                    Resolve.quietly(() -> call.resolve().declaringType().getQualifiedName())));
        }
        for (FieldAccessExpr access : cu.findAll(FieldAccessExpr.class)) {
            renameTypeScope(file, context, edits, access.getScope(),
                    Resolve.quietly(() -> access.resolve().asField().declaringType().getQualifiedName()));
        }
    }

    private void renameTypeScope(Path file, RuleContext context, List<TextEdit> edits,
                                 Expression scope, Optional<String> declaringType) {
        if (!(scope instanceof NameExpr nameExpr)
                || !nameExpr.getNameAsString().equals(oldSimpleName)) {
            return;
        }
        if (declaringType.map(qualifiedName::equals).orElse(false)) {
            nameExpr.getName().getRange().ifPresent(range ->
                    edits.add(context.replace(file, range, newSimpleName, id, order)));
        }
    }
}
