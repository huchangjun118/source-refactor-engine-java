package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskCancelledException;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cancellation/timeout must abort the task before anything is written and must
 * not leave a corrupt cache behind; results must be identical regardless of
 * the degree of parallelism.
 */
class CancellationAndConcurrencyTest {

    private static Map<String, String> project(int clients) {
        Map<String, String> files = new HashMap<>();
        files.put("com/acme/Service.java", """
                package com.acme;

                public class Service {
                    public String oldCall() {
                        return "x";
                    }
                }
                """);
        for (int i = 0; i < clients; i++) {
            files.put("com/acme/Client" + i + ".java", """
                    package com.acme;

                    public class Client%d {
                        public String go(Service s) {
                            return s.oldCall();
                        }
                    }
                    """.formatted(i));
        }
        return files;
    }

    private static RefactorConfig config(Path dir) {
        return TestSupport.config(dir,
                TestSupport.renameMethod("r1", 1, "com.acme.Service", "oldCall", "newCall"));
    }

    @Test
    void timeoutAbortsBeforeAnythingIsWrittenAndCacheSurvives(@TempDir Path dir) {
        Map<String, String> files = project(200);
        TestSupport.writeProject(dir, files);

        RefactorConfig tight = config(dir);
        tight.setTimeoutMillis(1); // expire almost immediately
        assertThrows(TaskCancelledException.class, () -> new RefactorEngine(tight).apply());

        // not a single file was modified
        for (Map.Entry<String, String> entry : files.entrySet()) {
            assertEquals(entry.getValue(), TestSupport.read(dir, entry.getKey()));
        }

        // a subsequent normal run works: the cache left behind is usable, not corrupt
        TaskResult result = new RefactorEngine(config(dir)).apply();
        assertTrue(TestSupport.read(dir, "com/acme/Client0.java").contains("newCall"));
        assertEquals(files.size(), result.filesScanned());
    }

    @Test
    void interruptCancelsTheTask(@TempDir Path dir) {
        TestSupport.writeProject(dir, project(50));
        Thread.currentThread().interrupt(); // simulate a cancelled caller
        try {
            RefactorConfig config = config(dir);
            config.setCacheEnabled(false);
            assertThrows(TaskCancelledException.class, () -> new RefactorEngine(config).apply());
        } finally {
            Thread.interrupted(); // clear the flag for the test framework
        }
    }

    @Test
    void resultsAreIndependentOfParallelism(@TempDir Path dir) {
        Map<String, String> files = project(60);
        TestSupport.writeProject(dir, files);

        RefactorConfig sequential = config(dir);
        sequential.setParallelism(1);
        sequential.setCacheEnabled(false);
        TaskResult one = new RefactorEngine(sequential).preview();

        RefactorConfig parallel = config(dir);
        parallel.setParallelism(8);
        parallel.setCacheEnabled(false);
        parallel.setStateDir(dir.resolve(".refactor-par"));
        TaskResult many = new RefactorEngine(parallel).preview();

        assertEquals(one.manifest().toJson(), many.manifest().toJson());
        assertEquals(one.newContents(), many.newContents());
    }
}
