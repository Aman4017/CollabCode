package com.codecollab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CorsConfig — Configures Cross-Origin Resource Sharing (CORS) for REST API endpoints.
 *
 * WHAT IS CORS?
 * -------------
 * Browsers enforce a security rule called the "Same-Origin Policy": a web page at
 * https://my-frontend.vercel.app can only make API calls to https://my-frontend.vercel.app
 * by default. If the frontend tries to call a DIFFERENT domain (like
 * https://my-backend.onrender.com), the browser blocks the request.
 *
 * CORS is the mechanism that allows the backend to say: "It's OK, I trust requests
 * from these specific domains." Without this configuration, the frontend would
 * get "CORS error" in the browser console and all API calls would fail.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * It tells Spring Boot which frontend domains are allowed to call the REST API
 * endpoints (like /api/compile and /api/health). The allowed domains are read
 * from the environment variable CORS_ALLOWED_ORIGINS (or application.properties).
 *
 * HOW IT WORKS:
 * -------------
 * 1. @Configuration tells Spring this class contains configuration settings.
 * 2. The class implements WebMvcConfigurer, which lets us customize how Spring
 *    handles HTTP requests. Specifically, we override addCorsMappings().
 * 3. The allowed origins come from the property "app.cors.allowed-origins" which
 *    defaults to "*" (allow all) if not set. In production, this should be set
 *    to the frontend URL (e.g., "https://collab-code-dusky.vercel.app").
 *
 * NOTE: This only applies to REST API endpoints (/api/**). WebSocket CORS is
 * configured separately in WebSocketConfig.java.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * The raw comma-separated string of allowed origins, injected from the
     * application property "app.cors.allowed-origins".
     *
     * Examples of what this value could be:
     * - "*"                                          (allow all origins — development)
     * - "https://collab-code-dusky.vercel.app"       (single origin — production)
     * - "http://localhost:3000, https://myapp.com"   (multiple origins)
     *
     * The ":*" after the property name means: if the property is not defined
     * anywhere, default to "*" (allow all origins).
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * Configures which origins (domains) can make REST API calls to this backend.
     *
     * This method is called automatically by Spring during startup. It registers
     * CORS rules that the server checks on every incoming HTTP request.
     *
     * How the browser CORS flow works:
     * 1. Browser sends a "preflight" OPTIONS request asking "Can I call this API?"
     * 2. Server responds with the allowed origins, methods, and headers.
     * 3. If the browser's origin matches, it proceeds with the actual request.
     * 4. If not, the browser blocks the request and shows a CORS error.
     *
     * @param registry The CorsRegistry provided by Spring — we add our rules to it.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Parse the comma-separated origins string into a clean array.
        // "http://localhost:3000, https://myapp.com" becomes ["http://localhost:3000", "https://myapp.com"]
        // .trim() removes extra whitespace, .filter() removes empty strings.
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        // Apply CORS rules to all endpoints under /api/**
        registry.addMapping("/api/**")
                .allowedOrigins(origins)                                     // Which domains can call us
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // Which HTTP methods are allowed
                .allowedHeaders("*");                                        // Allow any HTTP headers
    }
}
