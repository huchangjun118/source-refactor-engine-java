package com.github.huchangjun118.sourcerefactorenginejava.tests.refactor;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A JSON config file drives a full task, exactly as documented. */
class ConfigLoadingTest {

    @Test
    void jsonConfigDrivesATask(@TempDir Path dir) throws Exception {
        TestSupport.writeProject(dir, Map.of(
                "com/acme/Service.java", """
                        package com.acme;

                        public class Service {
                            public String oldCall() {
                                return "x";
                            }
                        }
                        """,
                "com/acme/Client.java", """
                        package com.acme;

                        public class Client {
                            public String go(Service s) {
                                return s.oldCall();
                            }
                        }
                        """));

        String json = """
                {
                  "sourceDir": "%s",
                  "onParseError": "SKIP",
                  "onUnresolved": "SKIP_WITH_WARNING",
                  "conflictStrategy": "FAIL",
                  "parallelism": 4,
                  "timeoutMillis": 0,
                  "cacheEnabled": true,
                  "rules": [
                    {
                      "id": "rename-call",
                      "type": "rename-method",
                      "order": 1,
                      "params": {
                        "owner": "com.acme.Service",
                        "oldName": "oldCall",
                        "newName": "newCall"
                      }
                    }
                  ]
                }
                """.formatted(dir.toString().replace("\\", "\\\\"));
        Path configFile = dir.resolve("refactor-config.json");
        Files.writeString(configFile, json, StandardCharsets.UTF_8);

        RefactorConfig config = RefactorConfig.load(configFile);
        assertEquals(1, config.getRules().size());
        assertEquals(4, config.getParallelism());

        TaskResult result = new RefactorEngine(config).apply();
        assertEquals(2, result.manifest().getChanges().size());
        assertTrue(TestSupport.read(dir, "com/acme/Client.java").contains("s.newCall()"));
    }
}
