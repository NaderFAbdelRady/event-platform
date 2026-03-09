package com.platform.websocket;

import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.websocket.Session;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory map of active WebSocket sessions.
 * Used by WsGatewayService to push messages to connected clients.
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class WebSocketSessionRegistry {

    // userId → WebSocket Session
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Lock(LockType.WRITE)
    public void register(String userId, Session session) {
        sessions.put(userId, session);
    }

    @Lock(LockType.WRITE)
    public void unregister(String userId) {
        sessions.remove(userId);
    }

    @Lock(LockType.READ)
    public Session getSession(String userId) {
        return sessions.get(userId);
    }

    @Lock(LockType.READ)
    public boolean isConnected(String userId) {
        Session s = sessions.get(userId);
        return s != null && s.isOpen();
    }

    @Lock(LockType.READ)
    public Set<String> getConnectedUserIds() {
        return Collections.unmodifiableSet(sessions.keySet());
    }

    @Lock(LockType.READ)
    public int size() {
        return sessions.size();
    }
}
