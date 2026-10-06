package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incremental runs must only recompute files affected by the input change and
 * must produce results identical to a full re-run.
 */
class IncrementalTest {

    private static Map<String, String> project() {
        Map<String, String> files = new HashMap<>();
        files.put("com/acme/Service.java", """
                package com.acme;

                public class Service {
                    public String oldCall() {
                        return "x";
                    }
                }
                """);
        for (int i = 0; i < 5; i++) {
            files.put("com/acme/Client" + i + ".java", """
                    package com.acme;

                    public class Client%d {
                        public String go(Service s) {
                            return s.oldCall();
                        }
                    }
                    """.formatted(i));
        }
        files.put("com/acme/Unrelated.java", """
                package com.acme;

                public class Unrelated {
                    public String hello() {
                        return "hello";
                    }
                }
                """);
        return files;
    }

    private static RefactorConfig config(Path dir, boolean cacheEnabled) {
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameMethod("r1", 1, "com.acme.Service", "oldCall", "newCall"));
        config.setCacheEnabled(cacheEnabled);
        return config;
    }

    @Test
    void onlyAffectedFilesAreRecomputedAndResultMatchesFullRun(@TempDir Path dir) {
        Map<String, String> files = project();
        TestSupport.writeProject(dir, files);

        // full preview run, populates the cache (working tree unchanged)
        TaskResult first = new RefactorEngine(config(dir, true)).preview();
        assertTrue(first.cacheMisses() > 0);
        int totalFiles = first.filesScanned();
        assertEquals(files.size(), totalFiles);

        // touch one unrelated file's method body (no declaration change)
        String touched = files.get("com/acme/Unrelated.java").replace("\"hello\"", "\"hello world\"");
        TestSupport.writeProject(dir, Map.of("com/acme/Unrelated.java", touched));

        // incremental run: only the touched file is re-parsed / re-computed
        TaskResult incremental = new RefactorEngine(config(dir, true)).preview();
        assertEquals(1, incremental.filesParsed(), "only the touched file should be re-parsed");
        assertEquals(1, incremental.cacheMisses(), "only the touched file should be re-computed");
        assertEquals(totalFiles - 1, incremental.cacheHits());

        // a full re-run without cache on the same input must agree exactly
        RefactorConfig noCache = config(dir, false);
        noCache.setStateDir(dir.resolve(".refactor-nocache"));
        TaskResult full = new RefactorEngine(noCache).preview();
        assertEquals(full.manifest().toJson(), incremental.manifest().toJson());
        assertEquals(full.newContents(), incremental.newContents());
    }

    @Test
    void declarationChangeInvalidatesAndStillMatchesFullRun(@TempDir Path dir) {
        Map<String, String> files = project();
        TestSupport.writeProject(dir, files);
        new RefactorEngine(config(dir, true)).apply();

        // change a declaration in an unrelated file
        String changed = files.get("com/acme/Unrelated.java")
                .replace("public String hello()", "public String hello(int loud)");
        TestSupport.writeProject(dir, Map.of("com/acme/Unrelated.java", changed));

        TaskResult incremental = new RefactorEngine(config(dir, true)).preview();

        RefactorConfig noCache = config(dir, false);
        noCache.setStateDir(dir.resolve(".refactor-nocache"));
        TaskResult full = new RefactorEngine(noCache).preview();
        assertEquals(full.manifest().toJson(), incremental.manifest().toJson());
        assertEquals(full.newContents(), incremental.newContents());
    }
}
