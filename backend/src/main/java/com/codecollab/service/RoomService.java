package com.codecollab.service;

import com.codecollab.model.UserSession;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomCode = new ConcurrentHashMap<>();

    /**
     * Adds a user to a room. Deduplicates by removing any existing session
     * with the same userName in the same room (handles reconnects / StrictMode).
     * Also removes user from any previous room if switching rooms.
     *
     * @return list of old session IDs that were removed (for closing stale WebSockets)
     */
    public synchronized List<String> addUser(String sessionId, String userName, String roomId) {
        List<String> removedSessionIds = new ArrayList<>();

        Set<String> room = rooms.get(roomId);
        if (room != null) {
            Iterator<String> it = room.iterator();
            while (it.hasNext()) {
                String existingId = it.next();
                UserSession existing = userSessions.get(existingId);
                if (existing != null && existing.getUserName().equals(userName)
                        && !existingId.equals(sessionId)) {
                    it.remove();
                    userSessions.remove(existingId);
                    removedSessionIds.add(existingId);
                }
            }
        }

        UserSession prev = userSessions.get(sessionId);
        if (prev != null && !prev.getRoomId().equals(roomId)) {
            Set<String> oldRoom = rooms.get(prev.getRoomId());
            if (oldRoom != null) {
                oldRoom.remove(sessionId);
                if (oldRoom.isEmpty()) {
                    rooms.remove(prev.getRoomId());
                    roomCode.remove(prev.getRoomId());
                }
            }
        }

        userSessions.put(sessionId, new UserSession(sessionId, userName, roomId));
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        return removedSessionIds;
    }

    public synchronized UserSession removeUser(String sessionId) {
        UserSession user = userSessions.remove(sessionId);
        if (user != null) {
            Set<String> room = rooms.get(user.getRoomId());
            if (room != null) {
                room.remove(sessionId);
                if (room.isEmpty()) {
                    rooms.remove(user.getRoomId());
                    roomCode.remove(user.getRoomId());
                }
            }
        }
        return user;
    }

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

    public UserSession getUser(String sessionId) {
        return userSessions.get(sessionId);
    }

    public void updateCode(String roomId, String code) {
        roomCode.put(roomId, code);
    }

    public String getCode(String roomId) {
        return roomCode.get(roomId);
    }
}
