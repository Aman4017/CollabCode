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

    public void addUser(String sessionId, String userName, String roomId) {
        userSessions.put(sessionId, new UserSession(sessionId, userName, roomId));
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public UserSession removeUser(String sessionId) {
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

    public List<UserSession> getRoomUsers(String roomId) {
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
