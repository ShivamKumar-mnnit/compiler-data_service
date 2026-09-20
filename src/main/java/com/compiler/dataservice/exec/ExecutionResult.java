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
    public static final String TIME_LIMIT_EXCEEDED = "TIME_LIMIT_EXCEEDED";
    public static final String MEMORY_LIMIT_EXCEEDED = "MEMORY_LIMIT_EXCEEDED";
}
