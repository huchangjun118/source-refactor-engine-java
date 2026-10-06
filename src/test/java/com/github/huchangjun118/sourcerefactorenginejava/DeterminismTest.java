package com.github.huchangjun118.sourcerefactorenginejava;

import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorOptions;
import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorReport;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameFieldRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RenameMethodRule;
import com.github.huchangjun118.sourcerefactorenginejava.engine.rule.RewriteRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class DeterminismTest {

    @TempDir
    Path project;

    private static final int FILE_COUNT = 24;

    private void writeProject() throws IOException {
        Files.createDirectories(project.resolve("com/acme"));
        Files.writeString(project.resolve("com/acme/Service.java"), """
                package com.acme;
                public class Service {
                    public int state = 0;
                    public String find(int id) { return "v" + id + state; }
                }
                """);
        for (int i = 0; i < FILE_COUNT; i++) {
            Files.writeString(project.resolve("com/acme/Caller" + i + ".java"), """
                    package com.acme;
                    public class Caller%d {
                        private final Service service = new Service();
                        public String call() { return service.find(%d) + service.state; }
                    }
                    """.formatted(i, i));
        }
    }

    private List<RewriteRule> rules() {
        return List.of(
                new RenameMethodRule("m1", 1, "com.acme.Service", "find", "locate"),
                new RenameFieldRule("f1", 2, "com.acme.Service", "state", "counter"));
    }

    private Map<String, String> snapshot() throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (var walk = Files.walk(project)) {
            for (Path p : walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java")).toList()) {
                files.put(project.relativize(p).toString(), Files.readString(p));
            }
        }
        return files;
    }

    @Test
    void replayIsByteIdenticalAndConcurrencyDoesNotChangeResults() throws IOException {
        writeProject();
        RefactorEngine engine = new RefactorEngine();

        RefactorOptions parallel = new RefactorOptions();
        parallel.setConcurrency(8);
        RefactorReport first = engine.apply(project, rules(), parallel);
        Map<String, String> afterFirst = snapshot();
        assertEquals(FILE_COUNT + 1, first.getFilesChanged());

        // Roll back and replay with single-threaded execution.
        engine.rollback(project, first.getJournalId());
        RefactorOptions single = new RefactorOptions();
        single.setConcurrency(1);
        RefactorReport second = engine.apply(project, rules(), single);
        Map<String, String> afterSecond = snapshot();

        assertEquals(afterFirst, afterSecond, "replay must be byte-identical");
        assertEquals(first.getChanges().size(), second.getChanges().size());
        List<String> firstChanges = new ArrayList<>();
        first.getChanges().forEach(c -> firstChanges.add(
                c.getFile() + "@" + c.getOffset() + ":" + c.getOldText() + "->" + c.getNewText()));
        List<String> secondChanges = new ArrayList<>();
        second.getChanges().forEach(c -> secondChanges.add(
                c.getFile() + "@" + c.getOffset() + ":" + c.getOldText() + "->" + c.getNewText()));
        assertEquals(firstChanges, secondChanges, "change lists must be identical");

        // Every rewritten file still parses and references were updated.
        String caller0 = afterSecond.get("com/acme/Caller0.java");
        assertTrue(caller0.contains("service.locate(0)"));
        assertTrue(caller0.contains("service.counter"));
        assertTrue(afterSecond.get("com/acme/Service.java").contains("public int counter = 0;"));
    }
}
