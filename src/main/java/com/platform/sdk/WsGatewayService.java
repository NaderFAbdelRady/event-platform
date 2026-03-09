package com.platform.sdk;

/**
 * SDK Service — allows components to push data back to
 * connected web browser clients via WebSocket.
 * Injected by the engine at load time.
 */
public interface WsGatewayService {

    /**
     * Sends a message to a specific connected user.
     * If the user is not connected, the message is silently dropped.
     */
    void sendToUser(String userId, Object payload);

    /**
     * Broadcasts a message to ALL currently connected users.
     */
    void broadcast(Object payload);

    /**
     * Returns true if the given user currently has an active WebSocket session.
     */
    boolean isUserConnected(String userId);
}
