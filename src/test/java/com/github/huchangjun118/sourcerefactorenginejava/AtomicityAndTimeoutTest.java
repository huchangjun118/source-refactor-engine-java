package com.github.huchangjun118.sourcerefactorenginejava;

import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorOptions;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorReport;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorTaskException;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameMethodRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RewriteRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.tx.FileCommitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AtomicityAndTimeoutTest {

    @TempDir
    Path project;

    private Path write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private List<RewriteRule> rules() {
        return List.of(new RenameMethodRule("r1", 1, "", "go", "run"));
    }

    @Test
    void commitFailureRestoresEveryFile() throws IOException {
        Path a = write("com/acme/A.java", """
                package com.acme;
                public class A {
                    public void go() {}
                }
                """);
        Path b = write("com/acme/B.java", """
                package com.acme;
                public class B {
                    public void go() {}
                }
                """);
        String aBefore = Files.readString(a);
        String bBefore = Files.readString(b);

        // Committer that blows up after the first file move.
        FileCommitter failing = new FileCommitter(index -> {
            if (index == 0) {
                throw new IllegalStateException("simulated crash mid-commit");
            }
        });
        RefactorEngine engine = new RefactorEngine(failing);
        assertThrows(RuntimeException.class,
                () -> engine.apply(project, rules(), new RefactorOptions()));

        assertEquals(aBefore, Files.readString(a), "file A restored after mid-commit failure");
        assertEquals(bBefore, Files.readString(b), "file B never changed");
        // No temp files left behind.
        try (Stream<Path> walk = Files.walk(project)) {
            List<Path> temps = walk.filter(p -> p.getFileName().toString().contains("refactor-tmp"))
                    .toList();
            assertTrue(temps.isEmpty(), "no staged temp files remain");
        }
    }

    @Test
    void timeoutCancelsTaskAndWritesNothing() throws IOException {
        Path a = write("com/acme/A.java", """
                package com.acme;
                public class A {
                    public void go() {}
                }
                """);
        String before = Files.readString(a);

        RewriteRule slowRule = new RenameMethodRule("slow", 1, "", "go", "run") {
            @Override
            public List<com.github.huchangjun118.sourcerefactorenginejava.engine.edit.TextEdit>
                    computeEdits(Path file, com.github.javaparser.ast.CompilationUnit unit,
                                 com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RuleContext context) {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of();
            }
        };
        RefactorOptions options = new RefactorOptions();
        options.setTimeoutSeconds(1);
        RefactorEngine engine = new RefactorEngine();
        assertThrows(RefactorTaskException.class,
                () -> engine.apply(project, List.of(slowRule), options));
        assertEquals(before, Files.readString(a), "cancelled task must not write anything");
    }
}
