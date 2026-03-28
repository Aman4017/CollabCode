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
    public ResponseEntity<Map<String, String>> compile(@RequestBody Map<String, String> request) {
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
