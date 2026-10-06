package com.github.huchangjun118.sourcerefactorenginejava;

import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorOptions;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorReport;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameMethodRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RewriteRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MethodRenameTest {

    @TempDir
    Path project;

    private static final String SERVICE = """
            package com.acme;
            public class Service {
                public String find(int id) { return "find:" + id; }
            }
            """;

    private static final String CLIENT = """
            package com.acme;
            public class Client {
                private final Service service = new Service();
                private final Other other = new Other();
                public String go() {
                    // find should stay in comments
                    String literal = "find stays in strings";
                    return service.find(1) + other.find() + literal;
                }
            }
            """;

    private static final String OTHER = """
            package com.acme;
            public class Other {
                public String find() { return "other"; }
            }
            """;

    private void writeProject() throws IOException {
        Files.createDirectories(project.resolve("com/acme"));
        Files.writeString(project.resolve("com/acme/Service.java"), SERVICE);
        Files.writeString(project.resolve("com/acme/Client.java"), CLIENT);
        Files.writeString(project.resolve("com/acme/Other.java"), OTHER);
    }

    private List<RewriteRule> rules() {
        return List.of(new RenameMethodRule("r1", 1, "com.acme.Service", "find", "locate"));
    }

    @Test
    void renamesDeclarationAndReferencesButNotStringsCommentsOrUnrelatedTypes() throws IOException {
        writeProject();
        RefactorEngine engine = new RefactorEngine();
        RefactorOptions options = new RefactorOptions();

        RefactorReport preview = engine.preview(project, rules(), options);
        assertFalse(preview.isApplied());
        assertEquals(2, preview.getFilesChanged());
        // Service declaration + one call site in Client.
        assertEquals(2, preview.getChanges().size());
        // Preview must not touch the disk.
        assertEquals(SERVICE, Files.readString(project.resolve("com/acme/Service.java")));

        RefactorReport applied = engine.apply(project, rules(), options);
        assertTrue(applied.isApplied());
        assertNotNull(applied.getJournalId());
        // Preview and apply must agree exactly on the change list.
        assertEquals(preview.getChanges().size(), applied.getChanges().size());
        for (int i = 0; i < preview.getChanges().size(); i++) {
            var a = preview.getChanges().get(i);
            var b = applied.getChanges().get(i);
            assertEquals(a.getFile(), b.getFile());
            assertEquals(a.getOffset(), b.getOffset());
            assertEquals(a.getOldText(), b.getOldText());
            assertEquals(a.getNewText(), b.getNewText());
        }

        String service = Files.readString(project.resolve("com/acme/Service.java"));
        assertTrue(service.contains("public String locate(int id)"));
        assertTrue(service.contains("\"find:\" + id"), "string literal must stay untouched");

        String client = Files.readString(project.resolve("com/acme/Client.java"));
        assertTrue(client.contains("service.locate(1)"));
        assertTrue(client.contains("other.find()"), "unrelated type method must stay");
        assertTrue(client.contains("// find should stay in comments"));
        assertTrue(client.contains("\"find stays in strings\""));

        String other = Files.readString(project.resolve("com/acme/Other.java"));
        assertEquals(OTHER, other, "Other.java must be byte-identical");
    }

    @Test
    void secondRunIsIdempotentAndRollbackRestoresBytes() throws IOException {
        writeProject();
        RefactorEngine engine = new RefactorEngine();
        RefactorOptions options = new RefactorOptions();

        RefactorReport first = engine.apply(project, rules(), options);
        assertEquals(2, first.getFilesChanged());

        RefactorReport second = engine.apply(project, rules(), options);
        assertEquals(0, second.getFilesChanged(), "second run must produce no changes");
        assertTrue(second.getChanges().isEmpty());

        engine.rollback(project, first.getJournalId());
        assertEquals(SERVICE, Files.readString(project.resolve("com/acme/Service.java")));
        assertEquals(CLIENT, Files.readString(project.resolve("com/acme/Client.java")));
        assertEquals(OTHER, Files.readString(project.resolve("com/acme/Other.java")));
    }
}
