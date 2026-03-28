package com.codecollab.config;

import com.codecollab.handler.CollabWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    private final CollabWebSocketHandler collabWebSocketHandler;

    public WebSocketConfig(CollabWebSocketHandler collabWebSocketHandler) {
        this.collabWebSocketHandler = collabWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addHandler(collabWebSocketHandler, "/ws")
                .setAllowedOrigins(origins);
    }
}
