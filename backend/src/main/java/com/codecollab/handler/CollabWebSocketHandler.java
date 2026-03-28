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

    // Maps our generated sessionId <-> WebSocketSession
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
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = wsToSessionId.get(session.getId());
        if (sessionId == null) return;

        JsonNode msg = mapper.readTree(message.getPayload());
        String type = msg.has("type") ? msg.get("type").asText() : "";
        JsonNode data = msg.get("data");

        switch (type) {
            case "join" -> handleJoin(sessionId, data);
            case "code-change" -> handleCodeChange(sessionId, data);
            case "sync-code" -> handleSyncCode(sessionId, data);
            case "user-call" -> forwardToUser(data, "incomming-call", "to", "offer", sessionId);
            case "call-accepted" -> forwardToUser(data, "call-accepted", "to", "ans", sessionId);
            case "peer-nego-needed" -> forwardToUser(data, "peer-nego-needed", "to", "offer", sessionId);
            case "peer-nego-done" -> forwardToUser(data, "peer-nego-final", "to", "ans", sessionId);
            default -> log.warn("Unknown message type: {}", type);
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
        String roomId = data.get("roomId").asText();
        String userName = data.has("userName") && !data.get("userName").isNull()
                ? data.get("userName").asText() : "Anonymous";

        roomService.addUser(sessionId, userName, roomId);

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
        log.info("User '{}' joined room '{}'", userName, roomId);
    }

    private void handleCodeChange(String sessionId, JsonNode data) {
        UserSession user = roomService.getUser(sessionId);
        if (user == null) return;

        String code = data.has("code") ? data.get("code").asText() : "";
        roomService.updateCode(user.getRoomId(), code);

        ObjectNode payload = mapper.createObjectNode().put("code", code);
        broadcastToRoom(user.getRoomId(), "code-change", payload, sessionId);
    }

    private void handleSyncCode(String sessionId, JsonNode data) {
        String targetSocketId = data.has("socketId") ? data.get("socketId").asText() : null;
        if (targetSocketId == null) return;

        WebSocketSession targetSession = sessionById.get(targetSocketId);
        if (targetSession == null || !targetSession.isOpen()) return;

        String code = data.has("code") && !data.get("code").isNull()
                ? data.get("code").asText() : null;

        if (code != null) {
            sendEvent(targetSession, "code-change",
                    mapper.createObjectNode().put("code", code));
        }
    }

    /**
     * Forward a WebRTC signaling message to a specific user.
     */
    private void forwardToUser(JsonNode data, String eventType,
                               String targetField, String payloadField, String fromSessionId) {
        String targetId = data.has(targetField) ? data.get(targetField).asText() : null;
        if (targetId == null) return;

        WebSocketSession targetSession = sessionById.get(targetId);
        if (targetSession == null || !targetSession.isOpen()) return;

        ObjectNode payload = mapper.createObjectNode();
        payload.put("from", fromSessionId);
        if (data.has(payloadField)) {
            payload.set(payloadField, data.get(payloadField));
        }

        sendEvent(targetSession, eventType, payload);
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
