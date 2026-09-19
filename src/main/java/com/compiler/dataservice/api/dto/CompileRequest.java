package com.compiler.dataservice.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CompileRequest(
        @NotBlank(message = "language is required") String language,
        @NotBlank(message = "code is required") String code,
        String stdin
) {
}
