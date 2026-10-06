package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.commit.FileCommitter;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorException;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task touching several files is committed atomically: if any write fails,
 * every file is restored; a committed task can be rolled back as a whole.
 */
class RollbackTest {

    private static final String SERVICE = """
            package com.acme;

            public class Service {
                public String oldCall() {
                    return "x";
                }
            }
            """;

    private static final String CLIENT_A = """
            package com.acme;

            public class ClientA {
                public String go(Service s) {
                    return s.oldCall();
                }
            }
            """;

    private static final String CLIENT_B = """
            package com.acme;

            public class ClientB {
                public String go(Service s) {
                    return s.oldCall() + s.oldCall();
                }
            }
            """;

    private Map<String, String> project() {
        return Map.of(
                "com/acme/Service.java", SERVICE,
                "com/acme/ClientA.java", CLIENT_A,
                "com/acme/ClientB.java", CLIENT_B);
    }

    private RefactorConfig config(Path dir) {
        return TestSupport.config(dir,
                TestSupport.renameMethod("r1", 1, "com.acme.Service", "oldCall", "newCall"));
    }

    @Test
    void failedCommitRestoresEveryFile(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());

        // fail while writing the second file of the commit
        AtomicInteger writes = new AtomicInteger();
        FileCommitter failing = new FileCommitter() {
            @Override
            protected void hookBeforeWrite(Path target) throws IOException {
                if (writes.incrementAndGet() == 2) {
                    throw new IOException("simulated disk failure on " + target);
                }
            }
        };

        RefactorException error = assertThrows(RefactorException.class,
                () -> new RefactorEngine(config(dir), failing).apply());
        assertTrue(error.getMessage().contains("rolled back"), error.getMessage());

        // all-or-nothing: every file is byte-identical to the original
        assertEquals(SERVICE, TestSupport.read(dir, "com/acme/Service.java"));
        assertEquals(CLIENT_A, TestSupport.read(dir, "com/acme/ClientA.java"));
        assertEquals(CLIENT_B, TestSupport.read(dir, "com/acme/ClientB.java"));
    }

    @Test
    void committedTaskCanBeRolledBackAsAWhole(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());
        RefactorEngine engine = new RefactorEngine(config(dir));

        TaskResult applied = engine.apply();
        assertTrue(TestSupport.read(dir, "com/acme/Service.java").contains("newCall"));
        assertTrue(TestSupport.read(dir, "com/acme/ClientA.java").contains("newCall"));
        assertTrue(TestSupport.read(dir, "com/acme/ClientB.java").contains("newCall"));

        engine.rollback(applied.manifest().getTaskId());

        assertEquals(SERVICE, TestSupport.read(dir, "com/acme/Service.java"));
        assertEquals(CLIENT_A, TestSupport.read(dir, "com/acme/ClientA.java"));
        assertEquals(CLIENT_B, TestSupport.read(dir, "com/acme/ClientB.java"));
    }

    @Test
    void rollbackOfUnknownTaskFails(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());
        RefactorEngine engine = new RefactorEngine(config(dir));
        assertThrows(RefactorException.class, () -> engine.rollback("no-such-task"));
    }
}
