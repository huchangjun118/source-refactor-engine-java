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

class IncrementalTest {

    @TempDir
    Path project;

    private Path write(String relative, String content) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private void writeProject() throws IOException {
        write("com/acme/Service.java", """
                package com.acme;
                public class Service {
                    public String find(int id) { return "x" + id; }
                }
                """);
        write("com/acme/Caller1.java", """
                package com.acme;
                public class Caller1 {
                    private final Service service = new Service();
                    public String a() { return service.find(1); }
                }
                """);
        write("com/acme/Caller2.java", """
                package com.acme;
                public class Caller2 {
                    private final Service service = new Service();
                    public String b() { return service.find(2); }
                }
                """);
        write("com/acme/Unrelated.java", """
                package com.acme;
                public class Unrelated {
                    public int u() { return 42; }
                }
                """);
    }

    private static List<RewriteRule> rename(String from, String to) {
        return List.of(new RenameMethodRule("r1", 1, "com.acme.Service", from, to));
    }

    @Test
    void incrementalRunReusesUnaffectedFilesAndMatchesFullRun() throws IOException {
        writeProject();
        RefactorEngine engine = new RefactorEngine();
        List<RewriteRule> rules = rename("find", "locate");

        // Run 1: full computation, cache created.
        RefactorReport r1 = engine.apply(project, rules, new RefactorOptions());
        assertEquals(0, r1.getFilesReusedFromCache());
        assertEquals(3, r1.getFilesChanged());

        // Run 2 (idempotent): everything served from cache.
        RefactorReport r2 = engine.apply(project, rules, new RefactorOptions());
        assertEquals(0, r2.getFilesChanged());
        assertEquals(4, r2.getFilesReusedFromCache());

        // Teammate writes new code in the old style: a new overload of find
        // plus a new call site in Caller1. Only those two files change.
        write("com/acme/Service.java", """
                package com.acme;
                public class Service {
                    public String locate(int id) { return "x" + id; }
                    public String find(String name) { return name; }
                }
                """);
        write("com/acme/Caller1.java", """
                package com.acme;
                public class Caller1 {
                    private final Service service = new Service();
                    public String a() { return service.locate(1); }
                    public String a2() { return service.find("n"); }
                }
                """);

        // Run 3 (incremental): Service and Caller1 changed; Caller2 is
        // invalidated through its recorded dependency on Service (overload
        // resolution could change); only Unrelated comes from the cache.
        RefactorReport r3inc = engine.apply(project, rules, new RefactorOptions());
        assertEquals(1, r3inc.getFilesReusedFromCache(),
                "only Unrelated must come from cache");
        assertEquals(2, r3inc.getFilesChanged());
        assertTrue(Files.readString(project.resolve("com/acme/Service.java"))
                .contains("locate(String name)"));
        assertTrue(Files.readString(project.resolve("com/acme/Caller1.java"))
                .contains("service.locate(\"n\")"));

        // Roll back run 3 and redo it as a full run; results must be identical.
        engine.rollback(project, r3inc.getJournalId());
        RefactorOptions full = new RefactorOptions();
        full.setIncremental(false);
        RefactorReport r3full = engine.apply(project, rules, full);
        assertEquals(r3inc.getChanges().size(), r3full.getChanges().size());
        for (int i = 0; i < r3inc.getChanges().size(); i++) {
            assertEquals(r3inc.getChanges().get(i).getOffset(), r3full.getChanges().get(i).getOffset());
            assertEquals(r3inc.getChanges().get(i).getFile(), r3full.getChanges().get(i).getFile());
        }
        assertTrue(Files.readString(project.resolve("com/acme/Caller2.java"))
                .contains("service.locate(2)"));
    }

    @Test
    void dependencyChangeInvalidatesDependents() throws IOException {
        writeProject();
        RefactorEngine engine = new RefactorEngine();
        engine.apply(project, rename("find", "locate"), new RefactorOptions());

        // User manually renames the method back in Service only.
        write("com/acme/Service.java", """
                package com.acme;
                public class Service {
                    public String find(int id) { return "x" + id; }
                }
                """);
        RefactorReport report = engine.apply(project, rename("find", "locate"), new RefactorOptions());
        // Service recomputed (hash changed); callers recomputed via dependency
        // invalidation; only Unrelated may come from cache.
        assertTrue(report.getFilesReusedFromCache() <= 1,
                "dependents must be invalidated, reused=" + report.getFilesReusedFromCache());
        assertTrue(Files.readString(project.resolve("com/acme/Service.java")).contains("locate"));
    }

    @Test
    void corruptCacheFallsBackToFullComputation() throws IOException {
        writeProject();
        RefactorEngine engine = new RefactorEngine();
        engine.apply(project, rename("find", "locate"), new RefactorOptions());

        Path cache = project.resolve(".refactor-engine/cache.json");
        assertTrue(Files.isRegularFile(cache));
        Files.writeString(cache, "{ not json !!!");

        write("com/acme/Service.java", """
                package com.acme;
                public class Service {
                    public String find(int id) { return "x" + id; }
                }
                """);
        RefactorReport report = engine.apply(project, rename("find", "locate"), new RefactorOptions());
        assertEquals(0, report.getFilesReusedFromCache(), "corrupt cache => full recompute");
        assertTrue(Files.readString(project.resolve("com/acme/Service.java")).contains("locate"));
        // Cache file is valid JSON again afterwards.
        assertTrue(Files.readString(cache).contains("\"ruleSetHash\""));
    }
}
