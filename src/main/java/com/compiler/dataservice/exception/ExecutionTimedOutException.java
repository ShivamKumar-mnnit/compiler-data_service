package com.compiler.dataservice.exception;

public class ExecutionTimedOutException extends RuntimeException {
    public ExecutionTimedOutException(String message) {
        super(message);
    }
}
