package com.github.huchangjun118.sourcerefactorenginejava.refactor.rule;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.Optional;

/** Small helpers to avoid raw-type pitfalls of the JavaParser AST API. */
final class TypeNames {

    private TypeNames() {
    }

    /** Fully qualified name of the innermost type declaration enclosing the node. */
    static Optional<String> enclosingTypeFqn(Node node) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent()) {
            Node n = current.get();
            if (n instanceof TypeDeclaration<?> td) {
                return td.getFullyQualifiedName();
            }
            current = n.getParentNode();
        }
        return Optional.empty();
    }
}
