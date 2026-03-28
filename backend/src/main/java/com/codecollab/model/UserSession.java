package com.codecollab.model;

public class UserSession {

    private final String sessionId;
    private final String userName;
    private final String roomId;

    public UserSession(String sessionId, String userName, String roomId) {
        this.sessionId = sessionId;
        this.userName = userName;
        this.roomId = roomId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserName() {
        return userName;
    }

    public String getRoomId() {
        return roomId;
    }
}
