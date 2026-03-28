package com.codecollab.controller;

import com.codecollab.service.CompilerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * CompilerController — The REST API controller that handles code compilation requests.
 *
 * WHAT IS A REST CONTROLLER?
 * --------------------------
 * A REST controller is a class that receives HTTP requests (like GET, POST) from
 * the frontend and sends back responses. Think of it as a "receptionist" — when
 * the frontend sends a request to a URL like /api/compile, Spring routes it to
 * the matching method in this class.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * It exposes two HTTP endpoints:
 *   1. POST /api/compile — Accepts code, input, and language; returns the output.
 *   2. GET  /api/health  — Returns {"status": "ok"} to verify the server is running.
 *
 * HOW THE COMPILE FLOW WORKS:
 * ---------------------------
 *   Frontend (browser)                    This Controller              CompilerService              Wandbox API
 *       |                                      |                            |                          |
 *       |--- POST /api/compile --------------->|                            |                          |
 *       |    { code, input, language }         |--- compile(code,input,lang)|                          |
 *       |                                      |                            |--- POST to Wandbox ----->|
 *       |                                      |                            |<--- compilation result --|
 *       |                                      |<--- output string ---------|                          |
 *       |<--- { "output": "Hello World" } -----|                            |                          |
 *
 * ANNOTATIONS EXPLAINED:
 * ----------------------
 * @RestController:       Marks this class as a REST API controller. Spring will:
 *                        a) Scan and register this class automatically.
 *                        b) Convert return values to JSON automatically.
 * @RequestMapping("/api"): All endpoints in this class start with /api.
 *                          So @PostMapping("/compile") becomes POST /api/compile.
 */
@RestController
@RequestMapping("/api")
public class CompilerController {

    /**
     * The service that handles the actual compilation logic.
     * Spring injects this automatically through the constructor (Dependency Injection).
     */
    private final CompilerService compilerService;

    /**
     * Constructor — Spring automatically provides the CompilerService instance.
     *
     * This pattern is called "Constructor Injection". Instead of creating
     * CompilerService ourselves, Spring creates it (because it's annotated with
     * @Service) and passes it here. This makes the code easier to test and maintain.
     *
     * @param compilerService The compilation service bean created by Spring.
     */
    public CompilerController(CompilerService compilerService) {
        this.compilerService = compilerService;
    }

    /**
     * POST /api/compile — Compiles and runs code in the specified language.
     *
     * This endpoint is called when the user clicks the "Run" button in the editor.
     * The frontend sends a JSON body like:
     *   {
     *     "code": "print('Hello')",
     *     "input": "",
     *     "language": "python"
     *   }
     *
     * The method extracts code, input, and language from the request body,
     * passes them to CompilerService.compile(), and returns the output.
     *
     * @param request A Map containing the request body fields:
     *                - "code":     The source code to compile/run (String).
     *                - "input":    Standard input for the program (String, can be empty).
     *                - "language": The programming language — "python", "java", "cpp", or "c".
     *                The @RequestBody annotation tells Spring to parse the JSON body
     *                into this Map. (required=false) means if no body is sent, request
     *                will be null instead of throwing an error.
     *
     * @return A ResponseEntity containing a Map with a single key "output" whose value
     *         is the program's output (or an error message). The HTTP status is:
     *         - 200 OK if compilation was attempted (even if the code has errors).
     *         - 400 Bad Request if no request body was provided.
     *
     * Example successful response:  {"output": "Hello World\n"}
     * Example error response:       {"output": "Error: Unsupported language 'ruby'"}
     */
    @PostMapping("/compile")
    public ResponseEntity<Map<String, String>> compile(
            @RequestBody(required = false) Map<String, String> request) {

        // Guard: if the request body is missing entirely, return 400 Bad Request
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("output", "Error: Request body is required"));
        }

        // Extract fields with safe defaults using getOrDefault()
        String code = request.getOrDefault("code", "");
        String input = request.getOrDefault("input", "");
        String language = request.getOrDefault("language", "python");

        // Delegate to the compiler service and return the result
        String output = compilerService.compile(code, input, language);
        return ResponseEntity.ok(Map.of("output", output));
    }

    /**
     * GET /api/health — A simple health check endpoint.
     *
     * Returns {"status": "ok"} to confirm the server is running and responsive.
     * This is used by:
     * - Deployment platforms (Render) to verify the app is alive.
     * - Developers to quickly check if the backend is accessible.
     * - Monitoring tools to track uptime.
     *
     * @return ResponseEntity with {"status": "ok"} and HTTP 200 status.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
