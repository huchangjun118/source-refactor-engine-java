package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multiple rules in one task: a type rename followed by a method rename whose
 * owner is the *renamed* type (sequential dependency between rules). Also
 * covers import rewriting and constructor/type references.
 */
class MultiRuleTest {

    private static final String OLD_SERVICE = """
            package com.acme;

            public class OldService {
                public OldService() {
                }

                public String oldMethod() {
                    return "v";
                }
            }
            """;

    private static final String CLIENT = """
            package com.acme.client;

            import com.acme.OldService;

            public class Client {
                private final OldService service = new OldService();

                public String call() {
                    OldService other = service;
                    return other.oldMethod();
                }
            }
            """;

    @Test
    void typeRenameThenMethodRenameOnTheRenamedType(@TempDir Path dir) {
        TestSupport.writeProject(dir, Map.of(
                "com/acme/OldService.java", OLD_SERVICE,
                "com/acme/client/Client.java", CLIENT));
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameType("t1", 1, "com.acme.OldService", "NewService"),
                TestSupport.renameMethod("m1", 2, "com.acme.NewService", "oldMethod", "newMethod"));

        TaskResult result = new RefactorEngine(config).apply();

        String service = TestSupport.read(dir, "com/acme/OldService.java");
        assertTrue(service.contains("public class NewService"));
        assertTrue(service.contains("public NewService()"));
        assertTrue(service.contains("public String newMethod()"));

        String client = TestSupport.read(dir, "com/acme/client/Client.java");
        assertTrue(client.contains("import com.acme.NewService;"));
        assertTrue(client.contains("private final NewService service = new NewService();"));
        assertTrue(client.contains("NewService other = service;"));
        assertTrue(client.contains("other.newMethod();"));

        // both rules contributed to the manifest
        assertTrue(result.manifest().getChanges().stream().anyMatch(c -> c.ruleId().equals("t1")));
        assertTrue(result.manifest().getChanges().stream().anyMatch(c -> c.ruleId().equals("m1")));

        // idempotent: a second run changes nothing
        TaskResult second = new RefactorEngine(config).apply();
        assertTrue(second.manifest().getChanges().isEmpty());
        assertEquals(service, TestSupport.read(dir, "com/acme/OldService.java"));
        assertEquals(client, TestSupport.read(dir, "com/acme/client/Client.java"));
    }
}
