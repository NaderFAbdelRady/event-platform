package com.platform.service;

import com.platform.sdk.WsGatewayService;
import com.platform.websocket.WebSocketSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.websocket.Session;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
public class WsGatewayServiceImpl implements WsGatewayService {

    private static final Logger LOG = Logger.getLogger(WsGatewayServiceImpl.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    @EJB
    private WebSocketSessionRegistry sessionRegistry;

    @Override
    public void sendToUser(String userId, Object payload) {
        Session session = sessionRegistry.getSession(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(JSON.writeValueAsString(payload));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to send message to user: " + userId, e);
            }
        }
    }

    @Override
    public void broadcast(Object payload) {
        String json;
        try {
            json = JSON.writeValueAsString(payload);
        } catch (Exception e) {
            LOG.warning("Failed to serialize broadcast payload");
            return;
        }
        for (String userId : sessionRegistry.getConnectedUserIds()) {
            Session session = sessionRegistry.getSession(userId);
            if (session != null && session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Broadcast failed for user: " + userId, e);
                }
            }
        }
    }

    @Override
    public boolean isUserConnected(String userId) {
        return sessionRegistry.isConnected(userId);
    }
}
