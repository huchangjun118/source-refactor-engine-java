package com.github.huchangjun118.sourcerefactorenginejava;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Application.class);
        for (String arg : args) {
            if (arg.startsWith("--refactor.config=")) {
                // CLI refactor invocation: no web server, quiet logs, clean
                // stdout for the change manifest, exit after the task.
                application.setWebApplicationType(WebApplicationType.NONE);
                application.setBannerMode(Banner.Mode.OFF);
                application.setDefaultProperties(Map.of(
                        "logging.level.root", "OFF",
                        "spring.main.log-startup-info", "false"));
                break;
            }
        }
        application.run(args);
    }
}
