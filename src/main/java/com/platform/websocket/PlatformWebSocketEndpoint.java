package com.platform.websocket;

import com.platform.events.EventContext;
import com.platform.events.EventSource;
import com.platform.events.PlatformEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ejb.EJB;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WebSocket endpoint for client connections.
 * Clients connect at: ws://host/ws/platform/{userId}
 *
 * Incoming messages from the browser are converted to PlatformEvents
 * and fired onto the CDI event bus.
 *
 * Components can push messages back to the browser via WsGatewayService.
 */
@ServerEndpoint(value = "/ws/platform/{userId}")
public class PlatformWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(PlatformWebSocketEndpoint.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    @EJB
    private WebSocketSessionRegistry sessionRegistry;

    @Inject
    private Event<PlatformEvent> eventBus;

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        sessionRegistry.register(userId, session);
        LOG.info("WebSocket connected: userId=" + userId + " sessionId=" + session.getId());

        // Fire a USER_CONNECTED event onto the bus
        PlatformEvent event = PlatformEvent
                .create("USER_CONNECTED", EventSource.FRONTEND)
                .withSourceId("/ws/platform/" + userId)
                .withContext(EventContext.of(userId, session.getId(), null))
                .addPayload("userId", userId);

        eventBus.fireAsync(event);
    }

    @OnMessage
    public void onMessage(String message,
                          Session session,
                          @PathParam("userId") String userId) {
        try {
            // Parse the incoming JSON message from browser
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JSON.readValue(message, Map.class);

            String eventType = (String) body.getOrDefault("eventType", "UNKNOWN");

            PlatformEvent event = PlatformEvent
                    .create(eventType, EventSource.FRONTEND)
                    .withSourceId("/ws/platform/" + userId)
                    .withContext(EventContext.of(userId, session.getId(), null))
                    .withPayload(body);

            // Fire onto the event bus — components will handle it
            eventBus.fireAsync(event);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse WebSocket message from user: " + userId, e);
        }
    }

    @OnClose
    public void onClose(Session session,
                        CloseReason reason,
                        @PathParam("userId") String userId) {
        sessionRegistry.unregister(userId);
        LOG.info("WebSocket disconnected: userId=" + userId
                + " reason=" + reason.getReasonPhrase());

        // Fire a USER_DISCONNECTED event
        PlatformEvent event = PlatformEvent
                .create("USER_DISCONNECTED", EventSource.FRONTEND)
                .withSourceId("/ws/platform/" + userId)
                .withContext(EventContext.of(userId, session.getId(), null))
                .addPayload("userId", userId);

        eventBus.fireAsync(event);
    }

    @OnError
    public void onError(Session session,
                        Throwable error,
                        @PathParam("userId") String userId) {
        LOG.log(Level.WARNING, "WebSocket error for user: " + userId, error);
        sessionRegistry.unregister(userId);
    }

    /**
     * Utility method — sends a message directly to a specific session.
     * Prefer using WsGatewayService from components.
     */
    public static void sendToSession(Session session, Object payload) {
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(JSON.writeValueAsString(payload));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to send WebSocket message", e);
            }
        }
    }
}
