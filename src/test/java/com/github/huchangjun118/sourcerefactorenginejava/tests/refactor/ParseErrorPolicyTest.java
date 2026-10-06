package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.ParseErrorPolicy;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorException;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.model.SkippedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unparseable files are either skipped (recorded with file and position,
 * content preserved byte-for-byte) or fail the whole task, per policy.
 */
class ParseErrorPolicyTest {

    private static final String GOOD = """
            package com.acme;

            public class Good {
                public String oldCall() {
                    return "ok";
                }
            }
            """;

    private static final String BROKEN = """
            package com.acme;

            public class Broken {
                public void oops( {
                    int x = ;
                }
            """;

    private Map<String, String> project() {
        return Map.of(
                "com/acme/Good.java", GOOD,
                "com/acme/Broken.java", BROKEN);
    }

    @Test
    void skipPolicyPreservesBrokenFileAndReportsPosition(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameMethod("r1", 1, "com.acme.Good", "oldCall", "newCall"));
        config.setOnParseError(ParseErrorPolicy.SKIP);

        TaskResult result = new RefactorEngine(config).apply();

        // the good file was rewritten
        assertTrue(TestSupport.read(dir, "com/acme/Good.java").contains("newCall"));
        // the broken file is preserved byte-for-byte
        assertEquals(BROKEN, TestSupport.read(dir, "com/acme/Broken.java"));
        // ... and reported with file, line and column
        assertEquals(1, result.manifest().getSkippedFiles().size());
        SkippedFile skipped = result.manifest().getSkippedFiles().get(0);
        assertEquals("com/acme/Broken.java", skipped.file());
        assertTrue(skipped.line() > 0, "line must be reported");
        assertTrue(skipped.column() > 0, "column must be reported");
    }

    @Test
    void failPolicyAbortsTheWholeTask(@TempDir Path dir) {
        TestSupport.writeProject(dir, project());
        RefactorConfig config = TestSupport.config(dir,
                TestSupport.renameMethod("r1", 1, "com.acme.Good", "oldCall", "newCall"));
        config.setOnParseError(ParseErrorPolicy.FAIL);

        RefactorException error = assertThrows(RefactorException.class,
                () -> new RefactorEngine(config).apply());
        assertTrue(error.getMessage().contains("com/acme/Broken.java"), error.getMessage());

        // nothing was written
        assertEquals(GOOD, TestSupport.read(dir, "com/acme/Good.java"));
        assertEquals(BROKEN, TestSupport.read(dir, "com/acme/Broken.java"));
    }
}
