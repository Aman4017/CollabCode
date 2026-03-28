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

/**
 * CompilerService — Handles code compilation by sending code to the Wandbox API.
 *
 * WHAT IS WANDBOX?
 * ----------------
 * Wandbox (https://wandbox.org) is a free online compiler service. Instead of
 * installing compilers (Python, Java, GCC) on our own server, we send the code
 * to Wandbox's API and it compiles/runs the code for us, returning the output.
 * This is ideal for a free deployment (like Render) where we can't install compilers.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * 1. Receives code, input, and language from CompilerController.
 * 2. Maps the language name (e.g., "python") to a Wandbox compiler name (e.g., "cpython-3.12.7").
 * 3. Sends an HTTP POST request to Wandbox's API with the code.
 * 4. Parses the response to extract the program output, errors, or compiler errors.
 * 5. Returns the result as a string back to the controller.
 *
 * SUPPORTED LANGUAGES:
 * --------------------
 * - "python" → cpython-3.12.7
 * - "java"   → openjdk-jdk-22+36
 * - "cpp"    → gcc-13.2.0
 * - "c"      → gcc-13.2.0-c
 *
 * ANNOTATIONS EXPLAINED:
 * ----------------------
 * @Service: Tells Spring "this class contains business logic, please create one
 *           instance of it and make it available for injection into other classes."
 *           Spring will automatically provide this to CompilerController.
 */
@Service
public class CompilerService {

    /** The URL of Wandbox's compilation API endpoint. */
    private static final String WANDBOX_API_URL = "https://wandbox.org/api/compile.json";

    /**
     * Maps our language identifiers to Wandbox's specific compiler names.
     * Wandbox requires the exact compiler name (not just "python").
     * Map.of() creates an immutable (unchangeable) map.
     */
    private static final Map<String, String> COMPILER_MAP = Map.of(
            "python", "cpython-3.12.7",
            "java", "openjdk-jdk-22+36",
            "cpp", "gcc-13.2.0",
            "c", "gcc-13.2.0-c"
    );

    /** The set of language names we support, derived from COMPILER_MAP's keys. */
    private static final Set<String> SUPPORTED_LANGUAGES = COMPILER_MAP.keySet();

    /**
     * RestTemplate is Spring's HTTP client — it sends HTTP requests to external APIs.
     * Think of it as the Java equivalent of JavaScript's fetch() or Python's requests.
     */
    private final RestTemplate restTemplate;

    /**
     * ObjectMapper is Jackson's JSON parser — it converts between Java objects and JSON strings.
     * Used to build the request body and parse the response from Wandbox.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructor — Creates the RestTemplate with timeout settings.
     *
     * RestTemplateBuilder is a helper provided by Spring Boot that lets us
     * configure the HTTP client. We set two timeouts:
     * - Connect timeout (10s): Maximum time to wait for a connection to Wandbox.
     * - Read timeout (30s): Maximum time to wait for Wandbox to respond after connecting.
     *
     * Without timeouts, if Wandbox is slow or down, our server would hang indefinitely
     * waiting for a response, eventually running out of threads and crashing.
     *
     * @param restTemplateBuilder Spring's builder for creating configured RestTemplate instances.
     */
    public CompilerService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Compiles and runs the given source code using the Wandbox API.
     *
     * This is the main method of this service. It:
     * 1. Validates that the language is supported.
     * 2. Builds a JSON request body with the code, compiler name, and stdin input.
     * 3. Sends an HTTP POST to Wandbox.
     * 4. Parses the JSON response to extract output/errors.
     * 5. Returns the result as a human-readable string.
     *
     * WANDBOX API REQUEST FORMAT:
     *   POST https://wandbox.org/api/compile.json
     *   {
     *     "code": "print('hello')",
     *     "compiler": "cpython-3.12.7",
     *     "stdin": ""
     *   }
     *
     * WANDBOX API RESPONSE FORMAT:
     *   {
     *     "status": "0",              // Exit code: "0" = success
     *     "compiler_error": "",       // Compilation errors (if any)
     *     "program_output": "hello",  // Standard output of the program
     *     "program_error": ""         // Runtime errors / stderr
     *   }
     *
     * @param code     The source code to compile and run.
     * @param input    The standard input (stdin) to feed to the program. Can be empty.
     * @param language The programming language: "python", "java", "cpp", or "c".
     *
     * @return The program output, error messages, or a status message. This string
     *         is sent directly to the frontend and displayed in the Output textarea.
     */
    public String compile(String code, String input, String language) {
        // Step 1: Validate language
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            return "Error: Unsupported language '" + language + "'. Supported: " +
                    String.join(", ", SUPPORTED_LANGUAGES);
        }

        try {
            // Step 2: Look up the Wandbox compiler name for this language
            String compiler = COMPILER_MAP.get(language);

            // Step 3: Build the JSON request body
            Map<String, Object> requestBody = Map.of(
                    "code", code,
                    "compiler", compiler,
                    "stdin", input != null ? input : ""
            );

            // Step 4: Set HTTP headers (tell Wandbox we're sending JSON)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Step 5: Create the HTTP request entity (body + headers combined)
            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);

            // Step 6: Send the POST request to Wandbox and get the response
            ResponseEntity<String> response = restTemplate.exchange(
                    WANDBOX_API_URL, HttpMethod.POST, entity, String.class);

            // Step 7: Parse the response
            String body = response.getBody();
            if (body == null) return "Error: Empty response from compiler service";

            JsonNode root = objectMapper.readTree(body);

            // Step 8: Extract the relevant fields from the response
            String compilerError = getField(root, "compiler_error");
            String programOutput = getField(root, "program_output");
            String programError = getField(root, "program_error");

            // Step 9: Return the most relevant output
            // Priority: compiler errors > program output + errors > exit code
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

            // If no output at all, report the exit code
            String status = getField(root, "status");
            return status.equals("0") ? "Program executed successfully (no output)" : "Exit code: " + status;

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Safely extracts a string field from a JSON node.
     *
     * JSON responses can have missing or null fields. This helper method
     * checks if the field exists and is not null before reading its value.
     * If the field is missing or null, it returns an empty string.
     *
     * @param root  The root JSON node (the parsed response from Wandbox).
     * @param field The name of the field to extract (e.g., "program_output").
     *
     * @return The field's string value, or "" if the field is missing/null.
     */
    private String getField(JsonNode root, String field) {
        return root.has(field) && !root.get(field).isNull()
                ? root.get(field).asText() : "";
    }
}
