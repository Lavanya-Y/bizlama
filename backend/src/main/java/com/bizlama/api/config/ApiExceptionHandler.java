package com.bizlama.api.config;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, Object>> illegalState(IllegalStateException error) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "detail", error.getMessage(),
                        "timestamp", Instant.now().toString()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(
            MethodArgumentNotValidException error
    ) {
        String detail = error.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(item -> item.getField() + ": " + item.getDefaultMessage())
                .orElse("Invalid request");

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "detail", detail,
                        "timestamp", Instant.now().toString()
                ));
    }
}