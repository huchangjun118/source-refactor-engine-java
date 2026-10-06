package com.github.huchangjun118.sourcerefactorenginejava.api;

import com.github.huchangjun118.sourcerefactorenginejava.engine.RefactorTaskException;
import com.github.huchangjun118.sourcerefactorenginejava.engine.parse.ProjectParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Maps engine failures to HTTP responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ProjectParseException.class)
    public ResponseEntity<Map<String, Object>> unparseable(ProjectParseException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", e.getMessage(), "issues", e.getIssues()));
    }

    @ExceptionHandler(RefactorTaskException.class)
    public ResponseEntity<Map<String, Object>> taskFailed(RefactorTaskException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }
}
