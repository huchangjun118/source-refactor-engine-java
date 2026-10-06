package com.github.huchangjun118.sourcerefactorenginejava;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RefactorControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @TempDir
    Path project;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void previewApplyRollbackOverHttp() throws IOException {
        Files.createDirectories(project.resolve("com/acme"));
        String original = """
                package com.acme;
                public class Service {
                    public void go() {}
                }
                """;
        Files.writeString(project.resolve("com/acme/Service.java"), original);

        Map<String, Object> request = Map.of(
                "projectDir", project.toString(),
                "rules", List.of(Map.of(
                        "kind", "rename-method",
                        "id", "r1",
                        "order", 1,
                        "ownerType", "com.acme.Service",
                        "oldName", "go",
                        "newName", "run")));

        var preview = rest.postForEntity(url("/api/refactor/preview"), request, Map.class);
        assertEquals(200, preview.getStatusCode().value());
        assertEquals(Boolean.FALSE, preview.getBody().get("applied"));
        assertEquals(1, ((List<?>) preview.getBody().get("changes")).size());
        // Preview wrote nothing.
        assertEquals(original, Files.readString(project.resolve("com/acme/Service.java")));

        var apply = rest.postForEntity(url("/api/refactor/apply"), request, Map.class);
        assertEquals(200, apply.getStatusCode().value());
        assertEquals(Boolean.TRUE, apply.getBody().get("applied"));
        String journalId = (String) apply.getBody().get("journalId");
        assertNotNull(journalId);
        assertTrue(Files.readString(project.resolve("com/acme/Service.java")).contains("void run()"));

        var rollback = rest.postForEntity(url("/api/refactor/rollback"),
                Map.of("projectDir", project.toString(), "journalId", journalId), Map.class);
        assertEquals(200, rollback.getStatusCode().value());
        assertEquals(original, Files.readString(project.resolve("com/acme/Service.java")));
    }

    @Test
    void unknownRuleKindIsRejected() {
        Map<String, Object> request = Map.of(
                "projectDir", project.toString(),
                "rules", List.of(Map.of("kind", "delete-everything", "id", "x")));
        var response = rest.postForEntity(url("/api/refactor/preview"), request, Map.class);
        assertEquals(400, response.getStatusCode().value());
    }
}
