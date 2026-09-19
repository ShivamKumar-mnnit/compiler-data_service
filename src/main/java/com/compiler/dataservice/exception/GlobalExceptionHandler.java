package com.compiler.dataservice.exception;

import com.compiler.dataservice.api.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(QueueFullException.class)
    public ResponseEntity<ErrorResponse> handleQueueFull(QueueFullException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(ExecutionTimedOutException.class)
    public ResponseEntity<ErrorResponse> handleExecutionTimeout(ExecutionTimedOutException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(UnsupportedLanguageException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedLanguage(UnsupportedLanguageException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unhandled error while processing request", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("Internal error"));
    }
}
