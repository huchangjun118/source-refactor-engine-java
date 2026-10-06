package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.ConflictStrategy;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorException;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two rules rewriting the same symbol: the conflict must be handled by the
 * configured deterministic strategy and must never be silently swallowed.
 */
class ConflictingRulesTest {

    private static final String SERVICE = """
            package com.acme;

            public class Service {
                public void run() {
                }
            }
            """;

    private static final String CLIENT = """
            package com.acme;

            public class Client {
                public void go(Service service) {
                    service.run();
                }
            }
            """;

    private Map<String, String> project() {
        return Map.of(
                "com/acme/Service.java", SERVICE,
                "com/acme/Client.java", CLIENT);
    }

    @Test
    void failStrategyAbortsAndWritesNothing(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameMethod("a", 1, "com.acme.Service", "run", "execute"),
                TestSupport.renameMethod("b", 2, "com.acme.Service", "execute", "launch"));
        config.setConflictStrategy(ConflictStrategy.FAIL);

        RefactorException error = assertThrows(RefactorException.class,
                () -> new RefactorEngine(config).apply());
        assertTrue(error.getMessage().contains("conflict"), error.getMessage());

        // nothing was written: the working tree is exactly as before
        assertEquals(SERVICE, TestSupport.read(dir, "com/acme/Service.java"));
        assertEquals(CLIENT, TestSupport.read(dir, "com/acme/Client.java"));
    }

    @Test
    void firstWinsStrategyIsDeterministicAndRecorded(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameMethod("a", 1, "com.acme.Service", "run", "execute"),
                TestSupport.renameMethod("b", 2, "com.acme.Service", "execute", "launch"));
        config.setConflictStrategy(ConflictStrategy.FIRST_WINS);

        TaskResult result = new RefactorEngine(config).apply();

        // the earlier rule (order 1) wins; the later rule's edits are dropped
        String service = TestSupport.read(dir, "com/acme/Service.java");
        String client = TestSupport.read(dir, "com/acme/Client.java");
        assertTrue(service.contains("public void execute()"));
        assertTrue(client.contains("service.execute();"));
        assertFalse(service.contains("launch"));
        assertFalse(client.contains("launch"));

        // the conflict is visible in the manifest, not swallowed
        assertFalse(result.manifest().getConflicts().isEmpty());
        assertTrue(result.manifest().getWarnings().stream()
                .anyMatch(w -> w.contains("'b'")), "dropped edits must be warned about");

        // replaying the same task on a fresh copy yields the same manifest bytes
        Path fresh = dir.resolve("fresh");
        TestSupport.writeProject(fresh, project());
        RefactorConfig freshConfig = TestSupport.config(fresh,
                TestSupport.renameMethod("a", 1, "com.acme.Service", "run", "execute"),
                TestSupport.renameMethod("b", 2, "com.acme.Service", "execute", "launch"));
        freshConfig.setConflictStrategy(ConflictStrategy.FIRST_WINS);
        TaskResult freshResult = new RefactorEngine(freshConfig).preview();
        assertEquals(result.manifest().toJson(), freshResult.manifest().toJson());
    }
}
