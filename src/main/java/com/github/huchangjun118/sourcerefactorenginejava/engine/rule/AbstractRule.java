package com.github.huchangjun118.sourcerefactorenginejava.engine.rule;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

/** Shared helpers for rename rules. */
abstract class AbstractRule implements RewriteRule {

    protected boolean ownerMatches(String actualQualifiedName, String expectedOwner) {
        if (expectedOwner == null || expectedOwner.isEmpty()) {
            return true;
        }
        return expectedOwner.equals(actualQualifiedName);
    }

    /** True when the node's compilation unit is one of the project's parsed files. */
    protected boolean isProjectNode(Node node, RuleContext context) {
        return node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(storage -> context.getUnits().containsKey(storage.getPath()))
                .orElse(false);
    }

    /** Validate that a name is a usable Java identifier (not a keyword/literal). */
    protected static void requireIdentifier(String name, String what) {
        if (name == null || name.isEmpty() || !name.chars().allMatch(Character::isJavaIdentifierPart)
                || !Character.isJavaIdentifierStart(name.charAt(0))
                || KEYWORDS.contains(name)) {
            throw new IllegalArgumentException(what + " is not a valid Java identifier: " + name);
        }
    }

    private static final java.util.Set<String> KEYWORDS = java.util.Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "true", "false", "null");
}
