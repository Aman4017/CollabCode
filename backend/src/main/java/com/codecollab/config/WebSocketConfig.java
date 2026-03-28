package com.codecollab.config;

import com.codecollab.handler.CollabWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * WebSocketConfig — Registers the WebSocket endpoint that the frontend connects to.
 *
 * WHAT IS A WEBSOCKET?
 * --------------------
 * A WebSocket is a persistent, two-way communication channel between the browser
 * and the server. Unlike regular HTTP (where the browser sends a request and gets
 * one response), a WebSocket stays open so both sides can send messages at any time.
 * This is essential for real-time features like:
 * - Live code synchronization (you type, others see it instantly)
 * - Chat messages (appear without refreshing the page)
 * - User join/leave notifications
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * It registers a WebSocket endpoint at the URL path "/ws". When the frontend
 * JavaScript calls `new WebSocket("wss://backend-url/ws")`, this is the endpoint
 * it connects to. All WebSocket messages are then handled by CollabWebSocketHandler.
 *
 * HOW IT WORKS:
 * -------------
 * 1. @EnableWebSocket tells Spring to activate WebSocket support.
 * 2. The class implements WebSocketConfigurer, which requires us to define
 *    registerWebSocketHandlers() — where we map URL paths to handler classes.
 * 3. We register CollabWebSocketHandler at "/ws" with the same CORS origins
 *    used by the REST API, so only trusted frontend domains can connect.
 *
 * CONNECTION FLOW:
 * ----------------
 * Frontend (browser)                    Backend (this server)
 *     |                                      |
 *     |--- ws://localhost:8080/ws ---------->|  (WebSocket handshake)
 *     |<--- 101 Switching Protocols ---------|  (Connection established)
 *     |                                      |
 *     |--- {"type":"join", "data":{...}} --->|  (Messages flow both ways)
 *     |<--- {"type":"joined", "data":{...}} -|
 *     |                                      |
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /**
     * Comma-separated list of allowed origins for WebSocket connections.
     * Read from the "app.cors.allowed-origins" property. Defaults to "*" (all origins).
     * In production, this should match the frontend deployment URL.
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * The handler that processes all WebSocket messages. Spring automatically
     * injects (provides) this because CollabWebSocketHandler is annotated with
     * @Component, so Spring creates an instance of it at startup.
     */
    private final CollabWebSocketHandler collabWebSocketHandler;

    /**
     * Constructor injection — Spring automatically passes in the CollabWebSocketHandler
     * instance it created. This is called "Dependency Injection": instead of us
     * creating the handler with `new CollabWebSocketHandler(...)`, Spring creates it
     * and gives it to us.
     *
     * @param collabWebSocketHandler The WebSocket message handler bean created by Spring.
     */
    public WebSocketConfig(CollabWebSocketHandler collabWebSocketHandler) {
        this.collabWebSocketHandler = collabWebSocketHandler;
    }

    /**
     * Registers the WebSocket endpoint and its handler.
     *
     * This method is called once during application startup. It maps:
     *   URL path "/ws" --> CollabWebSocketHandler
     *
     * So when a frontend client connects to ws://localhost:8080/ws (or wss:// in production),
     * Spring routes all messages to our CollabWebSocketHandler class.
     *
     * @param registry The WebSocketHandlerRegistry provided by Spring — we add our
     *                 handler mappings to it.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Parse allowed origins (same logic as CorsConfig)
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        // Register: all WebSocket connections to "/ws" are handled by collabWebSocketHandler
        // .setAllowedOrigins(origins) applies CORS rules to WebSocket connections
        registry.addHandler(collabWebSocketHandler, "/ws")
                .setAllowedOrigins(origins);
    }
}
