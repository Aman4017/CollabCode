package com.codecollab.service;

import com.codecollab.model.UserSession;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RoomService — Manages all rooms, users, and stored code in memory.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This is the "brain" of the collaboration system. It keeps track of:
 *   1. Which users are connected (userSessions map)
 *   2. Which users are in which rooms (rooms map)
 *   3. What code is currently in each room (roomCode map)
 *
 * Everything is stored IN MEMORY (not in a database), which means:
 *   - Data is lost when the server restarts.
 *   - It's fast (no database queries).
 *   - It works perfectly for a real-time collaboration tool where rooms are temporary.
 *
 * THREAD SAFETY:
 * --------------
 * Multiple users can join/leave rooms at the same time, which means multiple threads
 * might try to modify the same data simultaneously. Without protection, this could
 * corrupt the data (e.g., a user appearing twice, or a room not being cleaned up).
 *
 * We handle this with two mechanisms:
 *   1. ConcurrentHashMap: A thread-safe version of HashMap that allows safe concurrent reads.
 *   2. synchronized methods: For operations that need to read-then-write atomically
 *      (e.g., checking if a user exists then adding them), we use the "synchronized"
 *      keyword which ensures only one thread can execute the method at a time.
 *
 * DATA STRUCTURES:
 * ----------------
 *   userSessions: { "session-abc" -> UserSession("session-abc", "Alice", "room-1") }
 *                 Maps a session ID to the full user session object.
 *
 *   rooms:        { "room-1" -> Set["session-abc", "session-def"] }
 *                 Maps a room ID to the set of session IDs in that room.
 *
 *   roomCode:     { "room-1" -> "print('hello world')" }
 *                 Maps a room ID to the latest code written in that room.
 *
 * @Service tells Spring to create exactly one instance of this class (a "singleton")
 * and make it available for injection into other classes like CollabWebSocketHandler.
 */
@Service
public class RoomService {

    /** Maps session ID → UserSession. Every connected user has an entry here. */
    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    /** Maps room ID → Set of session IDs. Tracks which sessions are in which room. */
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();

    /** Maps room ID → latest code string. Stores the current code for each room. */
    private final Map<String, String> roomCode = new ConcurrentHashMap<>();

    /**
     * Adds a user to a room, handling deduplication and room-switching.
     *
     * This method handles several edge cases:
     *
     * 1. DUPLICATE USER PREVENTION:
     *    If the same userName already exists in the room (e.g., user refreshed their
     *    browser or React StrictMode caused a double-mount), the old session is removed.
     *    The removed session IDs are returned so the caller can close those WebSocket
     *    connections.
     *
     * 2. ROOM SWITCHING:
     *    If this sessionId was previously in a different room, they're removed from
     *    the old room first. If the old room becomes empty, it's cleaned up entirely.
     *
     * 3. ROOM CREATION:
     *    If the room doesn't exist yet, it's created automatically using
     *    computeIfAbsent().
     *
     * The "synchronized" keyword ensures this entire method runs atomically —
     * no other thread can call addUser(), removeUser(), or getRoomUsers() at the
     * same time, preventing race conditions.
     *
     * @param sessionId The unique identifier for this WebSocket connection.
     * @param userName  The display name chosen by the user.
     * @param roomId    The ID of the room to join.
     *
     * @return A list of old session IDs that were removed due to deduplication.
     *         The caller (CollabWebSocketHandler) uses these to close stale WebSocket
     *         connections. Returns an empty list if no duplicates were found.
     */
    public synchronized List<String> addUser(String sessionId, String userName, String roomId) {
        List<String> removedSessionIds = new ArrayList<>();

        // --- Step 1: Remove duplicate userNames from the target room ---
        Set<String> room = rooms.get(roomId);
        if (room != null) {
            Iterator<String> it = room.iterator();
            while (it.hasNext()) {
                String existingId = it.next();
                UserSession existing = userSessions.get(existingId);
                // If another session has the same userName in this room, remove it
                if (existing != null && existing.getUserName().equals(userName)
                        && !existingId.equals(sessionId)) {
                    it.remove();
                    userSessions.remove(existingId);
                    removedSessionIds.add(existingId);
                }
            }
        }

        // --- Step 2: If this session was in a different room, remove from old room ---
        UserSession prev = userSessions.get(sessionId);
        if (prev != null && !prev.getRoomId().equals(roomId)) {
            Set<String> oldRoom = rooms.get(prev.getRoomId());
            if (oldRoom != null) {
                oldRoom.remove(sessionId);
                // If the old room is now empty, clean it up entirely
                if (oldRoom.isEmpty()) {
                    rooms.remove(prev.getRoomId());
                    roomCode.remove(prev.getRoomId());
                }
            }
        }

        // --- Step 3: Add the user to the new room ---
        userSessions.put(sessionId, new UserSession(sessionId, userName, roomId));
        // computeIfAbsent: if the room doesn't exist, create a new Set for it
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        return removedSessionIds;
    }

    /**
     * Removes a user from their room and cleans up empty rooms.
     *
     * Called when a user disconnects (closes the browser tab, navigates away,
     * or loses connection). This method:
     * 1. Removes the user from the userSessions map.
     * 2. Removes their session ID from the room's member set.
     * 3. If the room is now empty, removes the room and its stored code.
     *
     * @param sessionId The unique session identifier of the disconnecting user.
     *
     * @return The UserSession that was removed, or null if no user was found
     *         for this session ID. The caller uses the returned UserSession
     *         to broadcast a "disconnected" event to the remaining room members.
     */
    public synchronized UserSession removeUser(String sessionId) {
        UserSession user = userSessions.remove(sessionId);
        if (user != null) {
            Set<String> room = rooms.get(user.getRoomId());
            if (room != null) {
                room.remove(sessionId);
                // Clean up empty rooms to prevent memory leaks
                if (room.isEmpty()) {
                    rooms.remove(user.getRoomId());
                    roomCode.remove(user.getRoomId());
                }
            }
        }
        return user;
    }

    /**
     * Returns a list of all users currently in a specific room.
     *
     * Used to:
     * - Send the client list when a new user joins (so they see who's already here).
     * - Broadcast messages to all users in a room.
     *
     * @param roomId The ID of the room to query.
     *
     * @return A list of UserSession objects for everyone in the room.
     *         Returns an empty list if the room doesn't exist.
     */
    public synchronized List<UserSession> getRoomUsers(String roomId) {
        Set<String> sessionIds = rooms.get(roomId);
        if (sessionIds == null) return Collections.emptyList();

        List<UserSession> users = new ArrayList<>();
        for (String id : sessionIds) {
            UserSession user = userSessions.get(id);
            if (user != null) users.add(user);
        }
        return users;
    }

    /**
     * Retrieves a single user's session by their session ID.
     *
     * Used to look up which room a user belongs to when processing their
     * messages (e.g., when they send a code change, we need to know which
     * room to broadcast it to).
     *
     * @param sessionId The unique session identifier.
     *
     * @return The UserSession object, or null if no user exists with this ID.
     */
    public UserSession getUser(String sessionId) {
        return userSessions.get(sessionId);
    }

    /**
     * Stores the latest code for a room.
     *
     * Called every time a user types in the editor. This ensures that when a new
     * user joins, they receive the current state of the code (not a blank editor).
     *
     * @param roomId The ID of the room whose code is being updated.
     * @param code   The full source code string.
     */
    public void updateCode(String roomId, String code) {
        roomCode.put(roomId, code);
    }

    /**
     * Retrieves the latest stored code for a room.
     *
     * Called when a new user joins a room — the server sends them the current code
     * so their editor is immediately synchronized with everyone else.
     *
     * @param roomId The ID of the room to get code for.
     *
     * @return The stored code string, or null if no code has been written in this room yet.
     */
    public String getCode(String roomId) {
        return roomCode.get(roomId);
    }
}
