package com.compiler.dataservice.exception;

public class QueueFullException extends RuntimeException {
    public QueueFullException(String message) {
        super(message);
    }
}
