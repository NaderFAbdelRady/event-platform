package com.platform.events;

import java.io.Serializable;

/**
 * Carries user/session context so components can filter
 * by who triggered the event and what role they have.
 */
public class EventContext implements Serializable {

    private String userId;
    private String sessionId;
    private String userRole;

    public EventContext() {}

    public EventContext(String userId, String sessionId, String userRole) {
        this.userId    = userId;
        this.sessionId = sessionId;
        this.userRole  = userRole;
    }

    public static EventContext of(String userId, String sessionId, String userRole) {
        return new EventContext(userId, sessionId, userRole);
    }

    public static EventContext system() {
        return new EventContext("SYSTEM", "SYSTEM", "SYSTEM");
    }

    // ── Getters / Setters ────────────────────────────────────

    public String getUserId()    { return userId; }
    public String getSessionId() { return sessionId; }
    public String getUserRole()  { return userRole; }

    public void setUserId(String userId)       { this.userId = userId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setUserRole(String userRole)   { this.userRole = userRole; }

    @Override
    public String toString() {
        return "EventContext{userId='" + userId + "', sessionId='" + sessionId
                + "', userRole='" + userRole + "'}";
    }
}
