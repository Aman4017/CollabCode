package com.codecollab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CompilerService {

    private static final String PISTON_API_URL = "https://emkc.org/api/v2/piston/execute";

    private static final Map<String, String> LANGUAGE_MAP = Map.of(
            "python", "python",
            "java", "java",
            "cpp", "cpp",
            "c", "c"
    );

    private static final Map<String, String> VERSION_MAP = Map.of(
            "python", "3.10.0",
            "java", "15.0.2",
            "cpp", "10.2.0",
            "c", "10.2.0"
    );

    private static final Set<String> SUPPORTED_LANGUAGES = LANGUAGE_MAP.keySet();

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
            Map<String, Object> requestBody = Map.of(
                    "language", LANGUAGE_MAP.get(language),
                    "version", VERSION_MAP.get(language),
                    "files", List.of(Map.of("content", code)),
                    "stdin", input != null ? input : ""
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    PISTON_API_URL, HttpMethod.POST, entity, String.class);

            String body = response.getBody();
            if (body == null) return "Error: Empty response from compiler service";

            JsonNode root = objectMapper.readTree(body);
            JsonNode run = root.get("run");

            if (run != null) {
                String stdout = run.has("stdout") ? run.get("stdout").asText() : "";
                String stderr = run.has("stderr") ? run.get("stderr").asText() : "";
                return stderr.isEmpty() ? stdout : stdout + "\n" + stderr;
            }

            return "Execution failed: No output received";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
