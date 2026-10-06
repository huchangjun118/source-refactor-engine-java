package com.github.huchangjun118.sourcerefactorenginejava.refactor.cli;

import com.github.huchangjun118.sourcerefactorenginejava.refactor.config.RefactorConfig;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.RefactorEngine;
import com.github.huchangjun118.sourcerefactorenginejava.refactor.engine.TaskResult;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional command line entry point. Inactive unless {@code --refactor.config}
 * is passed, so the application context stays quiet in tests.
 *
 * <pre>
 *   --refactor.config=path/to/config.json --refactor.mode=preview|apply
 *   --refactor.config=path/to/config.json --refactor.mode=rollback --refactor.task=&lt;taskId&gt;
 * </pre>
 *
 * Everything runs locally; the change manifest is printed to stdout.
 */
@Component
public class RefactorCli implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        Map<String, String> options = parseArgs(args);
        String configPath = options.get("refactor.config");
        if (configPath == null) {
            return; // not a refactor invocation
        }
        String mode = options.getOrDefault("refactor.mode", "preview");
        RefactorConfig config = RefactorConfig.load(Path.of(configPath));
        RefactorEngine engine = new RefactorEngine(config);
        switch (mode) {
            case "preview", "apply" -> {
                TaskResult result = mode.equals("preview") ? engine.preview() : engine.apply();
                System.out.println(result.manifest().toJson());
                System.err.printf("mode=%s committed=%s scanned=%d parsed=%d cacheHits=%d cacheMisses=%d changes=%d%n",
                        mode, result.committed(), result.filesScanned(), result.filesParsed(),
                        result.cacheHits(), result.cacheMisses(),
                        result.manifest().getChanges().size());
            }
            case "rollback" -> {
                String taskId = options.get("refactor.task");
                if (taskId == null) {
                    throw new IllegalArgumentException("--refactor.task=<taskId> is required for rollback");
                }
                engine.rollback(taskId);
                System.err.println("rolled back task " + taskId);
            }
            default -> throw new IllegalArgumentException("unknown --refactor.mode: " + mode);
        }
    }

    private static Map<String, String> parseArgs(String... args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 2) {
                    options.put(arg.substring(2, eq), arg.substring(eq + 1));
                } else {
                    options.put(arg.substring(2), "true");
                }
            }
        }
        return options;
    }
}
