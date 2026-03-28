package com.codecollab.controller;

import com.codecollab.service.CompilerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CompilerController {

    private final CompilerService compilerService;

    public CompilerController(CompilerService compilerService) {
        this.compilerService = compilerService;
    }

    @PostMapping("/compile")
    public ResponseEntity<Map<String, String>> compile(
            @RequestBody(required = false) Map<String, String> request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("output", "Error: Request body is required"));
        }

        String code = request.getOrDefault("code", "");
        String input = request.getOrDefault("input", "");
        String language = request.getOrDefault("language", "python");

        String output = compilerService.compile(code, input, language);
        return ResponseEntity.ok(Map.of("output", output));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
