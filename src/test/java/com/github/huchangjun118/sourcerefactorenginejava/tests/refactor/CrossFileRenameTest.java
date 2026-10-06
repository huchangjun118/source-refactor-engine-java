package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.ChangeEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-file symbol-level rename: every reference to the renamed method is
 * updated, while string literals, comments and same-named methods of other
 * types are left untouched. Preview and apply produce identical manifests.
 */
class CrossFileRenameTest {

    private static final String SERVICE = """
            package com.acme;

            public class Service {
                /** oldMethod does something. */
                public String oldMethod(int value) {
                    return "oldMethod:" + value;
                }
            }
            """;

    private static final String CALLER = """
            package com.acme;

            public class Caller {
                private final Service service = new Service();

                public String call() {
                    // oldMethod is called here
                    String label = "oldMethod";
                    return label + service.oldMethod(42) + this.service.oldMethod(7);
                }
            }
            """;

    private static final String OTHER = """
            package com.acme;

            public class Other {
                public String oldMethod() {
                    return "mine";
                }

                public String use() {
                    return oldMethod();
                }
            }
            """;

    private Map<String, String> project() {
        return Map.of(
                "com/acme/Service.java", SERVICE,
                "com/acme/Caller.java", CALLER,
                "com/acme/Other.java", OTHER);
    }

    @Test
    void renamesReferencesAcrossFilesButNotLiteralsOrComments(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameMethod("r1", 1, "com.acme.Service", "oldMethod", "newMethod"));
        RefactorEngine engine = new RefactorEngine(config);

        TaskResult preview = engine.preview();

        // working tree untouched by preview
        assertEquals(SERVICE, TestSupport.read(dir, "com/acme/Service.java"));
        assertEquals(CALLER, TestSupport.read(dir, "com/acme/Caller.java"));

        // manifest points at the declaration and both call sites
        assertEquals(3, preview.manifest().getChanges().size());
        for (ChangeEntry change : preview.manifest().getChanges()) {
            assertEquals("oldMethod", change.oldText());
            assertEquals("newMethod", change.newText());
            assertEquals("r1", change.ruleId());
            assertTrue(change.line() > 0 && change.column() > 0);
        }

        TaskResult applied = engine.apply();

        // preview and apply agree exactly
        assertEquals(preview.manifest().toJson(), applied.manifest().toJson());
        assertEquals(preview.newContents(), applied.newContents());

        String newCaller = TestSupport.read(dir, "com/acme/Caller.java");
        assertTrue(newCaller.contains("service.newMethod(42)"));
        assertTrue(newCaller.contains("this.service.newMethod(7)"));
        // literal and comment untouched
        assertTrue(newCaller.contains("\"oldMethod\""));
        assertTrue(newCaller.contains("// oldMethod is called here"));

        String newService = TestSupport.read(dir, "com/acme/Service.java");
        assertTrue(newService.contains("public String newMethod(int value)"));
        assertTrue(newService.contains("return \"oldMethod:\" + value;"));
        assertTrue(newService.contains("/** oldMethod does something. */"));

        // same-named method of another type untouched
        assertEquals(OTHER, TestSupport.read(dir, "com/acme/Other.java"));
        assertFalse(applied.newContents().containsKey("com/acme/Other.java"));
    }

    @Test
    void secondRunIsIdempotentAndReplayIsByteIdentical(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameMethod("r1", 1, "com.acme.Service", "oldMethod", "newMethod"));

        TaskResult first = new RefactorEngine(config).apply();
        Map<String, String> afterFirst = Map.of(
                "com/acme/Service.java", TestSupport.read(dir, "com/acme/Service.java"),
                "com/acme/Caller.java", TestSupport.read(dir, "com/acme/Caller.java"),
                "com/acme/Other.java", TestSupport.read(dir, "com/acme/Other.java"));

        TaskResult second = new RefactorEngine(config).apply();
        assertTrue(second.manifest().getChanges().isEmpty(), "second run must not change anything");
        assertTrue(second.newContents().isEmpty());
        for (Map.Entry<String, String> entry : afterFirst.entrySet()) {
            assertEquals(entry.getValue(), TestSupport.read(dir, entry.getKey()));
        }

        // replaying the preview on a fresh copy yields a byte-identical manifest
        Path replay = dir.resolve("replay");
        TestSupport.writeProject(replay, project());
        RefactorConfig replayConfig = TestSupport.config(replay,
                TestSupport.renameMethod("r1", 1, "com.acme.Service", "oldMethod", "newMethod"));
        replayConfig.setStateDir(dir.resolve(".refactor-replay"));
        TaskResult replayed = new RefactorEngine(replayConfig).preview();
        assertEquals(first.manifest().toJson(), replayed.manifest().toJson());
    }
}
