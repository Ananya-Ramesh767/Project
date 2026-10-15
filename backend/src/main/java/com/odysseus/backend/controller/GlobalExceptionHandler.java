package com.odysseus.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.HashMap;
import java.util.Map;

@lombok.extern.slf4j.Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Unauthorized");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleBadRequestExceptions(Exception ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Bad Request");
        response.put("message", "Invalid request data");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllOtherExceptions(Exception ex) {
        log.error("Unhandled exception in controller: ", ex);
        Map<String, String> response = new HashMap<>();
        response.put("error", "Internal Server Error");
        response.put("message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred on the server");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
