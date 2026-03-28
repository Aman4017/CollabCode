package com.codecollab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String compile(String code, String input, String language) {
        try {
            String pistonLang = LANGUAGE_MAP.getOrDefault(language, language);
            String version = VERSION_MAP.getOrDefault(language, "*");

            Map<String, Object> requestBody = Map.of(
                    "language", pistonLang,
                    "version", version,
                    "files", List.of(Map.of("content", code)),
                    "stdin", input != null ? input : ""
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    PISTON_API_URL, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
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
