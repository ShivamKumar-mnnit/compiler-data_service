package com.compiler.dataservice.api;

import com.compiler.dataservice.api.dto.CompileRequest;
import com.compiler.dataservice.api.dto.CompileResponse;
import com.compiler.dataservice.service.CompilationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CompileController {

    private final CompilationService compilationService;

    public CompileController(CompilationService compilationService) {
        this.compilationService = compilationService;
    }

    @PostMapping("/compile")
    public ResponseEntity<CompileResponse> compile(@Valid @RequestBody CompileRequest request) {
        return ResponseEntity.ok(compilationService.compile(request));
    }
}
