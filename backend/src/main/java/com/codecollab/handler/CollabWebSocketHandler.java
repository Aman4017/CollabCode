package com.codecollab.handler;

import com.codecollab.model.UserSession;
import com.codecollab.service.RoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollabWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CollabWebSocketHandler.class);

    private final RoomService roomService;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, WebSocketSession> sessionById = new ConcurrentHashMap<>();
    private final Map<String, String> wsToSessionId = new ConcurrentHashMap<>();

    public CollabWebSocketHandler(RoomService roomService) {
        this.roomService = roomService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketSession concurrentSession =
                new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024);

        String sessionId = UUID.randomUUID().toString();
        sessionById.put(sessionId, concurrentSession);
        wsToSessionId.put(session.getId(), sessionId);

        sendEvent(concurrentSession, "session-id", mapper.createObjectNode()
                .put("sessionId", sessionId));

        log.info("WebSocket connected: {}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = wsToSessionId.get(session.getId());
        if (sessionId == null) return;

        JsonNode msg;
        try {
            msg = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("Invalid JSON from session {}", sessionId);
            return;
        }

        String type = msg.has("type") ? msg.get("type").asText() : "";
        JsonNode data = msg.get("data");

        try {
            switch (type) {
                case "join" -> handleJoin(sessionId, data);
                case "code-change" -> handleCodeChange(sessionId, data);
                case "sync-code" -> handleSyncCode(sessionId, data);
                case "chat-message" -> handleChatMessage(sessionId, data);
                default -> log.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling '{}' from session {}", type, sessionId, e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = wsToSessionId.remove(session.getId());
        if (sessionId == null) return;

        sessionById.remove(sessionId);
        UserSession user = roomService.removeUser(sessionId);

        if (user != null) {
            ObjectNode payload = mapper.createObjectNode()
                    .put("socketId", sessionId)
                    .put("userName", user.getUserName());

            broadcastToRoom(user.getRoomId(), "disconnected", payload, sessionId);
            log.info("User '{}' left room '{}'", user.getUserName(), user.getRoomId());
        }
    }

    private void handleJoin(String sessionId, JsonNode data) {
        if (data == null || !data.hasNonNull("roomId")) {
            log.warn("Invalid join data from {}", sessionId);
            return;
        }

        String roomId = data.get("roomId").asText();
        String userName = data.hasNonNull("userName")
                ? data.get("userName").asText() : "Anonymous";

        List<String> removedIds = roomService.addUser(sessionId, userName, roomId);

        for (String removedId : removedIds) {
            WebSocketSession oldSession = sessionById.remove(removedId);
            if (oldSession != null && oldSession.isOpen()) {
                try {
                    oldSession.close(CloseStatus.NORMAL);
                } catch (IOException ignored) {
                }
            }
            log.info("Removed duplicate session {} for user '{}'", removedId, userName);
        }

        List<UserSession> users = roomService.getRoomUsers(roomId);
        ArrayNode clients = mapper.createArrayNode();
        for (UserSession u : users) {
            clients.add(mapper.createObjectNode()
                    .put("socketId", u.getSessionId())
                    .put("userName", u.getUserName()));
        }

        ObjectNode payload = mapper.createObjectNode();
        payload.set("clients", clients);
        payload.put("userName", userName);
        payload.put("socketId", sessionId);

        broadcastToRoom(roomId, "joined", payload, null);

        String storedCode = roomService.getCode(roomId);
        if (storedCode != null) {
            WebSocketSession joinerSession = sessionById.get(sessionId);
            if (joinerSession != null && joinerSession.isOpen()) {
                sendEvent(joinerSession, "code-change",
                        mapper.createObjectNode().put("code", storedCode));
            }
        }

        log.info("User '{}' joined room '{}'", userName, roomId);
    }

    private void handleCodeChange(String sessionId, JsonNode data) {
        if (data == null) return;

        UserSession user = roomService.getUser(sessionId);
        if (user == null) return;

        String code = data.has("code") ? data.get("code").asText() : "";
        roomService.updateCode(user.getRoomId(), code);

        ObjectNode payload = mapper.createObjectNode().put("code", code);
        broadcastToRoom(user.getRoomId(), "code-change", payload, sessionId);
    }

    private void handleSyncCode(String sessionId, JsonNode data) {
        if (data == null) return;

        String targetSocketId = data.hasNonNull("socketId")
                ? data.get("socketId").asText() : null;
        if (targetSocketId == null || targetSocketId.equals(sessionId)) return;

        WebSocketSession targetSession = sessionById.get(targetSocketId);
        if (targetSession == null || !targetSession.isOpen()) return;

        String code = data.hasNonNull("code") ? data.get("code").asText() : null;
        if (code != null) {
            sendEvent(targetSession, "code-change",
                    mapper.createObjectNode().put("code", code));
        }
    }

    private void handleChatMessage(String sessionId, JsonNode data) {
        if (data == null) return;

        UserSession user = roomService.getUser(sessionId);
        if (user == null) return;

        String message = data.hasNonNull("message") ? data.get("message").asText().trim() : "";
        if (message.isEmpty() || message.length() > 2000) return;

        ObjectNode payload = mapper.createObjectNode()
                .put("message", message)
                .put("userName", user.getUserName())
                .put("timestamp", System.currentTimeMillis());

        broadcastToRoom(user.getRoomId(), "chat-message", payload, sessionId);
    }

    private void broadcastToRoom(String roomId, String type, JsonNode data, String excludeSessionId) {
        List<UserSession> users = roomService.getRoomUsers(roomId);
        for (UserSession user : users) {
            if (user.getSessionId().equals(excludeSessionId)) continue;

            WebSocketSession ws = sessionById.get(user.getSessionId());
            if (ws != null && ws.isOpen()) {
                sendEvent(ws, type, data);
            }
        }
    }

    private void sendEvent(WebSocketSession session, String type, JsonNode data) {
        try {
            ObjectNode message = mapper.createObjectNode();
            message.put("type", type);
            message.set("data", data);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
        } catch (IOException e) {
            log.error("Failed to send message to session", e);
        }
    }
}
