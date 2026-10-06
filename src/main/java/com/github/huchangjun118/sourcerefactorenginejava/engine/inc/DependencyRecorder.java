package com.github.huchangjun118.sourcerefactorenginejava.engine.inc;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.Resolve;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Records which project files a single file's rule computation depended on
 * through symbol resolution. Used by the incremental cache: a cached result
 * stays valid only while every recorded dependency keeps its content hash.
 */
public class DependencyRecorder implements Resolve.ResolutionListener {
    private final Map<String, Path> typeIndex;            // qualified name -> file
    private final Map<String, TypeDeclaration<?>> typeAst; // qualified name -> declaration
    private final Set<Path> deps = new LinkedHashSet<>();
    private final Set<String> expandedTypes = new HashSet<>();
    private volatile boolean dependsOnAll;

    public DependencyRecorder(Map<String, Path> typeIndex, Map<String, TypeDeclaration<?>> typeAst) {
        this.typeIndex = typeIndex;
        this.typeAst = typeAst;
    }

    public Set<Path> getDeps() { return deps; }
    public boolean isDependsOnAll() { return dependsOnAll; }

    @Override
    public void onResolved(Object result) {
        if (result instanceof ResolvedMethodDeclaration rmd) {
            addType(rmd.declaringType().getQualifiedName());
        } else if (result instanceof ResolvedFieldDeclaration rfd) {
            addType(rfd.declaringType().getQualifiedName());
        } else if (result instanceof ResolvedReferenceTypeDeclaration rrtd) {
            addType(rrtd.getQualifiedName());
        } else if (result instanceof ResolvedType rt) {
            if (rt.isReferenceType()) {
                addType(rt.asReferenceType().getQualifiedName());
            }
        }
        // Local variables / parameters live in the same file: no dependency.
    }

    @Override
    public void onFailure() {
        // A failed resolution might succeed once some other file changes;
        // be conservative and recompute this file on any project change.
        dependsOnAll = true;
    }

    private void addType(String qualifiedName) {
        if (qualifiedName == null || !expandedTypes.add(qualifiedName)) {
            return;
        }
        Path file = typeIndex.get(qualifiedName);
        if (file == null) {
            return; // external type (JDK / library): not tracked
        }
        deps.add(file);
        // Supertypes influence resolution results (e.g. inherited members),
        // so changes there must invalidate this file too.
        TypeDeclaration<?> declaration = typeAst.get(qualifiedName);
        if (declaration != null) {
            Resolve.quietly(() -> declaration.resolve().getAllAncestors())
                    .ifPresent(ancestors -> {
                        for (var ancestor : ancestors) {
                            addType(ancestor.getQualifiedName());
                        }
                    });
        }
    }
}
