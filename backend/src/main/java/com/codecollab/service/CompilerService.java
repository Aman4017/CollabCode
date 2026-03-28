package com.codecollab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Service
public class CompilerService {

    private static final String WANDBOX_API_URL = "https://wandbox.org/api/compile.json";

    private static final Map<String, String> COMPILER_MAP = Map.of(
            "python", "cpython-3.12.7",
            "java", "openjdk-jdk-22+36",
            "cpp", "gcc-13.2.0",
            "c", "gcc-13.2.0-c"
    );

    private static final Set<String> SUPPORTED_LANGUAGES = COMPILER_MAP.keySet();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompilerService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String compile(String code, String input, String language) {
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            return "Error: Unsupported language '" + language + "'. Supported: " +
                    String.join(", ", SUPPORTED_LANGUAGES);
        }

        try {
            String compiler = COMPILER_MAP.get(language);

            Map<String, Object> requestBody = Map.of(
                    "code", code,
                    "compiler", compiler,
                    "stdin", input != null ? input : ""
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    WANDBOX_API_URL, HttpMethod.POST, entity, String.class);

            String body = response.getBody();
            if (body == null) return "Error: Empty response from compiler service";

            JsonNode root = objectMapper.readTree(body);

            String compilerError = getField(root, "compiler_error");
            String programOutput = getField(root, "program_output");
            String programError = getField(root, "program_error");

            if (!compilerError.isEmpty()) {
                return compilerError;
            }

            if (!programOutput.isEmpty() && !programError.isEmpty()) {
                return programOutput + "\n" + programError;
            }

            if (!programOutput.isEmpty()) {
                return programOutput;
            }

            if (!programError.isEmpty()) {
                return programError;
            }

            String status = getField(root, "status");
            return status.equals("0") ? "Program executed successfully (no output)" : "Exit code: " + status;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String getField(JsonNode root, String field) {
        return root.has(field) && !root.get(field).isNull()
                ? root.get(field).asText() : "";
    }
}
