package com.compiler.dataservice.exec;

public record ExecutionResult(
        String status,
        String stdout,
        String stderr,
        Integer exitCode,
        long executionTimeMs
) {
    public static final String SUCCESS = "SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String TIMEOUT = "TIMEOUT";
}
