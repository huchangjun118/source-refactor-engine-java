package com.github.huchangjun118.sourcerefactorenginejava;

import com.github.huchangjun118.sourcerefactorenginejava.engine.ParseErrorPolicy;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorOptions;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorReport;
import com.github.huchangjun118.sourcerefactorenginejava.engine.parse.ProjectParseException;
import com.github.huchangjun118.sourcerefactorenginejava.engine.plan.ConflictRecord;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameMethodRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RewriteRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConflictAndParsePolicyTest {

    @TempDir
    Path project;

    private Path write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    @Test
    void conflictingRulesAreReportedAndResolvedDeterministically() throws IOException {
        write("com/acme/A.java", """
                package com.acme;
                public class A {
                    public void go() {}
                }
                """);
        // Two rules rename the same method to different names.
        List<RewriteRule> rules = List.of(
                new RenameMethodRule("b-rule", 2, "com.acme.A", "go", "runB"),
                new RenameMethodRule("a-rule", 1, "com.acme.A", "go", "runA"));
        RefactorEngine engine = new RefactorEngine();
        RefactorReport report = engine.apply(project, rules, new RefactorOptions());

        assertEquals(1, report.getConflicts().size());
        ConflictRecord conflict = report.getConflicts().get(0);
        assertEquals("com/acme/A.java", conflict.getFile());
        assertTrue(conflict.getRuleIds().containsAll(List.of("a-rule", "b-rule")));
        assertEquals("a-rule", conflict.getWinnerRuleId(), "lower order wins");

        String a = Files.readString(project.resolve("com/acme/A.java"));
        assertTrue(a.contains("public void runA() {}"), "winner edit applied");

        // Replaying the same task on the original input gives byte-identical output.
        engine.rollback(project, report.getJournalId());
        RefactorReport replay = engine.apply(project, rules, new RefactorOptions());
        assertEquals(a, Files.readString(project.resolve("com/acme/A.java")));
        assertEquals(report.getChanges().size(), replay.getChanges().size());
    }

    @Test
    void identicalEditsFromTwoRulesDoNotConflict() throws IOException {
        write("com/acme/A.java", """
                package com.acme;
                public class A {
                    public void go() {}
                }
                """);
        List<RewriteRule> rules = List.of(
                new RenameMethodRule("r1", 1, "com.acme.A", "go", "run"),
                new RenameMethodRule("r2", 2, "com.acme.A", "go", "run"));
        RefactorEngine engine = new RefactorEngine();
        RefactorReport report = engine.apply(project, rules, new RefactorOptions());
        assertTrue(report.getConflicts().isEmpty(), "identical edits are deduped, not conflicts");
        assertTrue(Files.readString(project.resolve("com/acme/A.java")).contains("void run()"));
    }

    @Test
    void brokenFileIsSkippedWithPositionAndKeptAsIs() throws IOException {
        String broken = """
                package com.acme;
                public class Broken {
                    public void oops( {
                }
                """;
        write("com/acme/Broken.java", broken);
        write("com/acme/Ok.java", """
                package com.acme;
                public class Ok {
                    public void go() {}
                }
                """);
        RefactorOptions options = new RefactorOptions();
        options.setParseErrorPolicy(ParseErrorPolicy.SKIP);
        RefactorEngine engine = new RefactorEngine();
        RefactorReport report = engine.apply(project,
                List.of(new RenameMethodRule("r1", 1, "com.acme.Ok", "go", "run")), options);

        assertEquals(1, report.getSkippedFiles().size());
        var skipped = report.getSkippedFiles().get(0);
        assertEquals("com/acme/Broken.java", skipped.getFile());
        assertNotNull(skipped.getLine(), "skip must report the problem position");
        assertNotNull(skipped.getColumn());
        // Broken file preserved byte-for-byte; good file rewritten.
        assertEquals(broken, Files.readString(project.resolve("com/acme/Broken.java")));
        assertTrue(Files.readString(project.resolve("com/acme/Ok.java")).contains("void run()"));
    }

    @Test
    void failPolicyAbortsWholeTaskAndWritesNothing() throws IOException {
        String broken = "package com.acme;\npublic class Broken { void x( {\n}\n";
        write("com/acme/Broken.java", broken);
        Path ok = write("com/acme/Ok.java", """
                package com.acme;
                public class Ok {
                    public void go() {}
                }
                """);
        String okBefore = Files.readString(ok);
        RefactorOptions options = new RefactorOptions();
        options.setParseErrorPolicy(ParseErrorPolicy.FAIL);
        RefactorEngine engine = new RefactorEngine();
        ProjectParseException ex = assertThrows(ProjectParseException.class, () ->
                engine.apply(project,
                        List.of(new RenameMethodRule("r1", 1, "com.acme.Ok", "go", "run")),
                        options));
        assertFalse(ex.getIssues().isEmpty());
        assertNotNull(ex.getIssues().get(0).getLine());
        // Nothing was written.
        assertEquals(okBefore, Files.readString(ok));
        assertEquals(broken, Files.readString(project.resolve("com/acme/Broken.java")));
    }
}
