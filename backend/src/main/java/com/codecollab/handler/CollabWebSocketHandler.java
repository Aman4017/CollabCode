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

/**
 * CollabWebSocketHandler — The core WebSocket handler for all real-time collaboration.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This is the most important class in the backend. It processes ALL WebSocket messages
 * between the frontend and the server. Every time a user types code, sends a chat
 * message, joins a room, or disconnects, this class handles it.
 *
 * HOW WEBSOCKET MESSAGES WORK IN THIS APP:
 * -----------------------------------------
 * All messages are JSON objects with two fields:
 *   {
 *     "type": "join",              // The event type (what kind of action)
 *     "data": { "roomId": "..." }  // The event payload (the details)
 *   }
 *
 * Supported message types:
 *   INCOMING (from frontend → server):
 *     - "join"          → User wants to join a room
 *     - "code-change"   → User typed/modified code in the editor
 *     - "sync-code"     → User wants to send current code to a specific peer
 *     - "chat-message"  → User sent a chat message
 *
 *   OUTGOING (from server → frontend):
 *     - "session-id"    → Server assigns a unique session ID to the new connection
 *     - "joined"        → Notifies all room members that someone joined
 *     - "disconnected"  → Notifies all room members that someone left
 *     - "code-change"   → Broadcasts code changes to other room members
 *     - "chat-message"  → Broadcasts chat messages to other room members
 *
 * KEY CONCEPTS:
 * -------------
 * 1. TextWebSocketHandler: A Spring class we extend. It calls our methods when
 *    WebSocket events occur (connection opened, message received, connection closed).
 *
 * 2. ConcurrentWebSocketSessionDecorator: Wraps the raw WebSocket session to make
 *    it thread-safe. Without this, if two threads try to send messages to the same
 *    user at the same time, the messages could get corrupted.
 *
 * 3. sessionById / wsToSessionId: Two lookup maps that let us find WebSocket
 *    connections by our custom session IDs and vice versa.
 *
 * @Component tells Spring to create one instance of this class and manage it.
 * It's like @Service but more generic (used for any Spring-managed object).
 */
@Component
public class CollabWebSocketHandler extends TextWebSocketHandler {

    /**
     * Logger for outputting debug/info/error messages to the server console.
     * SLF4J is a logging framework — it's like System.out.println() but better
     * because it supports log levels (DEBUG, INFO, WARN, ERROR) and can be
     * configured to write to files.
     */
    private static final Logger log = LoggerFactory.getLogger(CollabWebSocketHandler.class);

    /** The room management service — handles user tracking and code storage. */
    private final RoomService roomService;

    /**
     * ObjectMapper from the Jackson library — converts between Java objects and JSON.
     * Used to parse incoming JSON messages and build outgoing JSON responses.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Maps our custom session ID → WebSocket session object.
     * When we need to send a message to a specific user, we look up their
     * WebSocket connection here using their session ID.
     *
     * ConcurrentHashMap is a thread-safe version of HashMap — multiple threads
     * can read from and write to it simultaneously without corruption.
     */
    private final Map<String, WebSocketSession> sessionById = new ConcurrentHashMap<>();

    /**
     * Maps Spring's internal WebSocket session ID → our custom session ID.
     * Spring assigns its own internal IDs to WebSocket connections. This map
     * lets us translate from Spring's IDs to our custom IDs (which we share
     * with the frontend).
     */
    private final Map<String, String> wsToSessionId = new ConcurrentHashMap<>();

    /**
     * Constructor — receives the RoomService instance from Spring's dependency injection.
     *
     * @param roomService The room management service.
     */
    public CollabWebSocketHandler(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * Called automatically by Spring when a new WebSocket connection is established.
     *
     * This happens right after the browser successfully connects via
     * `new WebSocket("ws://localhost:8080/ws")`. At this point, the user hasn't
     * joined a room yet — they've just established the WebSocket tunnel.
     *
     * What this method does:
     * 1. Wraps the raw session in ConcurrentWebSocketSessionDecorator for thread safety.
     *    - sendTimeLimit (5000ms): Maximum time to wait when sending a message.
     *    - bufferSizeLimit (512KB): Maximum amount of queued data before dropping messages.
     * 2. Generates a unique session ID (UUID) for this connection.
     * 3. Stores the session in our lookup maps.
     * 4. Sends the session ID back to the frontend so it knows its own ID.
     *
     * @param session The raw WebSocket session created by Spring.
     * @throws Exception If sending the session-id message fails.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Wrap for thread-safe concurrent message sending
        WebSocketSession concurrentSession =
                new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024);

        // Generate a unique ID for this connection
        String sessionId = UUID.randomUUID().toString();
        sessionById.put(sessionId, concurrentSession);
        wsToSessionId.put(session.getId(), sessionId);

        // Tell the frontend its assigned session ID
        sendEvent(concurrentSession, "session-id", mapper.createObjectNode()
                .put("sessionId", sessionId));

        log.info("WebSocket connected: {}", sessionId);
    }

    /**
     * Called automatically by Spring when a text message arrives on a WebSocket connection.
     *
     * This is the main message router. It:
     * 1. Looks up which custom session ID this WebSocket belongs to.
     * 2. Parses the JSON message to extract the "type" and "data" fields.
     * 3. Routes the message to the appropriate handler method based on "type".
     *
     * MESSAGE FORMAT (all messages follow this pattern):
     *   {
     *     "type": "join",                          // Determines which handler to call
     *     "data": { "roomId": "abc", "userName": "Alice" }  // Payload for the handler
     *   }
     *
     * @param session The WebSocket session that sent this message.
     * @param message The raw text message containing JSON.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Look up our custom session ID for this WebSocket
        String sessionId = wsToSessionId.get(session.getId());
        if (sessionId == null) return;

        // Parse the JSON message
        JsonNode msg;
        try {
            msg = mapper.readTree(message.getPayload());
        } catch (Exception e) {
            log.warn("Invalid JSON from session {}", sessionId);
            return;
        }

        // Extract the message type and data payload
        String type = msg.has("type") ? msg.get("type").asText() : "";
        JsonNode data = msg.get("data");

        // Route to the correct handler based on message type
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

    /**
     * Called automatically by Spring when a WebSocket connection is closed.
     *
     * This happens when the user closes their browser tab, navigates away,
     * or loses internet connection. This method:
     * 1. Cleans up the session from our lookup maps.
     * 2. Removes the user from their room via RoomService.
     * 3. Broadcasts a "disconnected" event to all remaining room members
     *    so their UI updates (removes the user's avatar from the sidebar).
     *
     * @param session The WebSocket session that was closed.
     * @param status  The reason for closing (e.g., normal closure, going away, etc.).
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Translate Spring's session ID to our custom ID, and clean up the mapping
        String sessionId = wsToSessionId.remove(session.getId());
        if (sessionId == null) return;

        // Remove from our session lookup
        sessionById.remove(sessionId);

        // Remove the user from their room and get their info
        UserSession user = roomService.removeUser(sessionId);

        if (user != null) {
            // Build and broadcast the "disconnected" event to remaining room members
            ObjectNode payload = mapper.createObjectNode()
                    .put("socketId", sessionId)
                    .put("userName", user.getUserName());

            broadcastToRoom(user.getRoomId(), "disconnected", payload, sessionId);
            log.info("User '{}' left room '{}'", user.getUserName(), user.getRoomId());
        }
    }

    /**
     * Handles a "join" message — a user wants to enter a room.
     *
     * This is one of the most important methods. When a user navigates to
     * /editor/room-abc and enters their name, the frontend sends:
     *   { "type": "join", "data": { "roomId": "room-abc", "userName": "Alice" } }
     *
     * What this method does:
     * 1. Validates the incoming data (roomId is required).
     * 2. Calls roomService.addUser() which handles deduplication and returns
     *    any stale session IDs that need to be closed.
     * 3. Closes any stale WebSocket connections (from the same user re-connecting).
     * 4. Builds a list of all users currently in the room.
     * 5. Broadcasts a "joined" event to ALL room members (including the joiner)
     *    with the full client list.
     * 6. Sends the room's existing code to the new joiner so their editor is
     *    immediately synchronized.
     *
     * @param sessionId The session ID of the user who wants to join.
     * @param data      The JSON payload: { "roomId": "...", "userName": "..." }
     */
    private void handleJoin(String sessionId, JsonNode data) {
        // Validate required fields
        if (data == null || !data.hasNonNull("roomId")) {
            log.warn("Invalid join data from {}", sessionId);
            return;
        }

        String roomId = data.get("roomId").asText();
        String userName = data.hasNonNull("userName")
                ? data.get("userName").asText() : "Anonymous";

        // Add user to room (returns IDs of any duplicate sessions that were removed)
        List<String> removedIds = roomService.addUser(sessionId, userName, roomId);

        // Close WebSocket connections for removed duplicate sessions
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

        // Build the client list to send to all room members
        List<UserSession> users = roomService.getRoomUsers(roomId);
        ArrayNode clients = mapper.createArrayNode();
        for (UserSession u : users) {
            clients.add(mapper.createObjectNode()
                    .put("socketId", u.getSessionId())
                    .put("userName", u.getUserName()));
        }

        // Build the "joined" event payload
        ObjectNode payload = mapper.createObjectNode();
        payload.set("clients", clients);
        payload.put("userName", userName);
        payload.put("socketId", sessionId);

        // Broadcast to everyone in the room (including the joiner)
        broadcastToRoom(roomId, "joined", payload, null);

        // Send existing room code to the new joiner so their editor is in sync
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

    /**
     * Handles a "code-change" message — a user typed something in the editor.
     *
     * When a user types in the CodeMirror editor, the frontend sends:
     *   { "type": "code-change", "data": { "code": "print('hello')" } }
     *
     * This method:
     * 1. Looks up which room this user is in.
     * 2. Stores the updated code in RoomService (for future joiners).
     * 3. Broadcasts the code change to all OTHER users in the room
     *    (excludes the sender to avoid an echo loop).
     *
     * The exclusion of the sender is critical — if we sent the code back to the
     * sender, their editor would call setValue(), which would trigger another
     * code-change event, creating an infinite loop.
     *
     * @param sessionId The session ID of the user who typed.
     * @param data      The JSON payload: { "code": "..." }
     */
    private void handleCodeChange(String sessionId, JsonNode data) {
        if (data == null) return;

        // Look up which room this user is in
        UserSession user = roomService.getUser(sessionId);
        if (user == null) return;

        String code = data.has("code") ? data.get("code").asText() : "";

        // Store the code so new joiners get the latest version
        roomService.updateCode(user.getRoomId(), code);

        // Broadcast to everyone else in the room (exclude sender)
        ObjectNode payload = mapper.createObjectNode().put("code", code);
        broadcastToRoom(user.getRoomId(), "code-change", payload, sessionId);
    }

    /**
     * Handles a "sync-code" message — syncs the current code to a specific user.
     *
     * When a new user joins a room, existing users send their current code to
     * the new joiner. The frontend sends:
     *   { "type": "sync-code", "data": { "socketId": "target-session-id", "code": "..." } }
     *
     * This is a point-to-point message (not a broadcast). It sends the code
     * only to the specified target user.
     *
     * This serves as a backup synchronization mechanism. The server already sends
     * stored code to new joiners in handleJoin(), but this peer-to-peer sync
     * ensures the latest code is received even if the stored version is slightly
     * behind.
     *
     * @param sessionId The session ID of the user sending the sync.
     * @param data      The JSON payload: { "socketId": "target-id", "code": "..." }
     */
    private void handleSyncCode(String sessionId, JsonNode data) {
        if (data == null) return;

        // Get the target user's session ID
        String targetSocketId = data.hasNonNull("socketId")
                ? data.get("socketId").asText() : null;
        if (targetSocketId == null || targetSocketId.equals(sessionId)) return;

        // Find the target's WebSocket connection
        WebSocketSession targetSession = sessionById.get(targetSocketId);
        if (targetSession == null || !targetSession.isOpen()) return;

        // Send the code to the target user only
        String code = data.hasNonNull("code") ? data.get("code").asText() : null;
        if (code != null) {
            sendEvent(targetSession, "code-change",
                    mapper.createObjectNode().put("code", code));
        }
    }

    /**
     * Handles a "chat-message" message — a user sent a chat message.
     *
     * When a user types a chat message and clicks "Send" (or presses Enter),
     * the frontend sends:
     *   { "type": "chat-message", "data": { "message": "Hello everyone!" } }
     *
     * This method:
     * 1. Looks up the sender's userName from their session.
     * 2. Validates the message (not empty, not too long).
     * 3. Broadcasts the message to all OTHER users in the room.
     *    (The sender already adds the message to their own UI locally.)
     *
     * The message length is capped at 2000 characters to prevent abuse.
     *
     * @param sessionId The session ID of the user who sent the message.
     * @param data      The JSON payload: { "message": "Hello everyone!" }
     */
    private void handleChatMessage(String sessionId, JsonNode data) {
        if (data == null) return;

        // Look up the sender's user session
        UserSession user = roomService.getUser(sessionId);
        if (user == null) return;

        // Extract and validate the message
        String message = data.hasNonNull("message") ? data.get("message").asText().trim() : "";
        if (message.isEmpty() || message.length() > 2000) return;

        // Build the chat message payload with sender info and timestamp
        ObjectNode payload = mapper.createObjectNode()
                .put("message", message)
                .put("userName", user.getUserName())
                .put("timestamp", System.currentTimeMillis());

        // Broadcast to everyone in the room except the sender
        broadcastToRoom(user.getRoomId(), "chat-message", payload, sessionId);
    }

    /**
     * Sends a message to ALL users in a room, optionally excluding one user.
     *
     * This is the workhorse method used by almost every handler. It:
     * 1. Gets the list of all users in the room from RoomService.
     * 2. Iterates through each user.
     * 3. Skips the excluded user (if specified) — usually the sender.
     * 4. Sends the event to each remaining user's WebSocket connection.
     *
     * @param roomId           The room to broadcast to.
     * @param type             The event type (e.g., "joined", "code-change", "chat-message").
     * @param data             The event payload (a JSON object).
     * @param excludeSessionId The session ID to exclude from the broadcast, or null
     *                         to send to everyone. Usually the sender's ID to prevent
     *                         echo-back.
     */
    private void broadcastToRoom(String roomId, String type, JsonNode data, String excludeSessionId) {
        List<UserSession> users = roomService.getRoomUsers(roomId);
        for (UserSession user : users) {
            // Skip the excluded user (typically the message sender)
            if (user.getSessionId().equals(excludeSessionId)) continue;

            WebSocketSession ws = sessionById.get(user.getSessionId());
            if (ws != null && ws.isOpen()) {
                sendEvent(ws, type, data);
            }
        }
    }

    /**
     * Sends a single JSON event to a specific WebSocket session.
     *
     * Builds a JSON message in the format: { "type": "...", "data": {...} }
     * and sends it over the WebSocket connection. If sending fails (e.g., the
     * connection was just closed), it logs the error but doesn't crash.
     *
     * @param session The WebSocket session to send the message to.
     * @param type    The event type string (e.g., "joined", "code-change").
     * @param data    The event payload as a JSON node.
     */
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
