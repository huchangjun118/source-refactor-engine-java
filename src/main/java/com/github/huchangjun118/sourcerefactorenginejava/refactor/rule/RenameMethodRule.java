package com.github.huchangjun118.sourcerefactorenginejava.refactor.rule;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.TextEdit;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.parse.ParsedFile;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * Renames a method of a given owner type and updates all call sites that
 * resolve to that type. String literals, comments and unrelated methods that
 * merely share the name are never touched: only the declaration whose
 * declaring type matches {@code owner} and references that the symbol solver
 * resolves to {@code owner} are rewritten.
 */
public class RenameMethodRule implements RewriteRule {

    private final String id;
    private final int order;
    private final String owner;
    private final String oldName;
    private final String newName;

    public RenameMethodRule(String id, int order, String owner, String oldName, String newName) {
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
        for (MethodDeclaration md : file.cu().findAll(MethodDeclaration.class)) {
            if (!md.getNameAsString().equals(oldName)) {
                continue;
            }
            boolean inOwner = TypeNames.enclosingTypeFqn(md)
                    .map(fqn -> owner.equals(fqn))
                    .orElse(false);
            if (inOwner) {
                edits.add(rename(file, md, md.getNameAsString()));
            }
        }

        // 2. Call sites resolved through the symbol solver.
        for (MethodCallExpr call : file.cu().findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals(oldName)) {
                continue;
            }
            try {
                ResolvedMethodDeclaration resolved = call.resolve();
                if (resolved.declaringType().getQualifiedName().equals(owner)) {
                    edits.add(rename(file, call, call.getNameAsString()));
                }
            } catch (Throwable t) {
                ctx.unresolved("method call '" + oldName + "(...)'", call.getName().getRange());
            }
        }

        // 3. Method references such as Service::oldMethod.
        for (MethodReferenceExpr ref : file.cu().findAll(MethodReferenceExpr.class)) {
            if (!ref.getIdentifier().equals(oldName)) {
                continue;
            }
            try {
                ResolvedMethodDeclaration resolved = ref.resolve();
                if (resolved.declaringType().getQualifiedName().equals(owner)) {
                    ref.getRange().ifPresent(r -> {
                        // identifier sits at the end of the range, after '::'
                        int endOffset = file.offset(r.end) + 1;
                        int startOffset = endOffset - oldName.length();
                        String oldText = file.content().substring(startOffset, endOffset);
                        if (oldText.equals(oldName)) {
                            edits.add(new TextEdit(file.relPath(), startOffset, oldName.length(), oldName, newName, id));
                        } else {
                            ctx.unresolved("method reference '" + oldName + "'", ref.getRange());
                        }
                    });
                }
            } catch (Throwable t) {
                ctx.unresolved("method reference '" + oldName + "'", ref.getRange());
            }
        }

        return edits;
    }

    private TextEdit rename(ParsedFile file, NodeWithSimpleName<?> node, String name) {
        return file.edit(node.getName().getRange()
                .orElseThrow(() -> new IllegalStateException("node without range")), newName, id);
    }
}
