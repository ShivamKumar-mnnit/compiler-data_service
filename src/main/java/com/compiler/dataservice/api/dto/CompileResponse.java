package com.compiler.dataservice.api.dto;

public record CompileResponse(
        String status,
        String stdout,
        String stderr,
        Integer exitCode,
        long executionTimeMs
) {
}
