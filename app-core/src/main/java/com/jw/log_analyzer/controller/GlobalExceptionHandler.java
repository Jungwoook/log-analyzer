package com.jw.log_analyzer.controller;

import com.jw.log_analyzer.parser.contract.exception.InvalidLogFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidLogFormatException.class)
    public ResponseEntity<Map<String, String>> handleInvalidLogFormat(InvalidLogFormatException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", exception.getMessage()));
    }
}
