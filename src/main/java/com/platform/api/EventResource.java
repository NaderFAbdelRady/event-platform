package com.platform.api;

import com.platform.events.EventContext;
import com.platform.events.EventSource;
import com.platform.events.PlatformEvent;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST endpoint to fire events onto the platform event bus.
 * Used by the Modeler UI and any external system.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    private static final Logger LOG = Logger.getLogger(EventResource.class.getName());

    @Inject
    private Event<PlatformEvent> eventBus;

    @POST
    @Path("/fire")
    public Response fireEvent(FireEventRequest req,
                              @Context SecurityContext sc) {
        try {
            // Validate required fields
            if (req == null) {
                return error(400, "Request body is required");
            }
            if (req.getEventType() == null || req.getEventType().isBlank()) {
                return error(400, "eventType is required");
            }

            // Resolve source — default to EXTERNAL if missing or invalid
            EventSource source;
            try {
                source = EventSource.valueOf(
                        req.getSource() != null ? req.getSource().toUpperCase() : "EXTERNAL");
            } catch (IllegalArgumentException e) {
                source = EventSource.EXTERNAL;
            }

            // Resolve userId from SecurityContext or request body
            String userId = "anonymous";
            if (sc.getUserPrincipal() != null) {
                userId = sc.getUserPrincipal().getName();
            } else if (req.getContext() != null && req.getContext().get("userId") != null) {
                userId = req.getContext().get("userId").toString();
            }

            // Build the event — null-safe payload
            Map<String, Object> payload = req.getPayload() != null
                    ? req.getPayload()
                    : new HashMap<>();

            PlatformEvent event = PlatformEvent
                    .create(req.getEventType(), source)
                    .withSourceId("/api/events/fire")
                    .withContext(EventContext.of(userId, null, null))
                    .withPayload(payload);

            if (req.getCorrelationId() != null) {
                event.withCorrelationId(req.getCorrelationId());
            }

            // Fire asynchronously — returns 202 immediately
            eventBus.fireAsync(event);

            LOG.info("Event fired: " + event.getEventType() + " id=" + event.getEventId());

            Map<String, Object> result = new HashMap<>();
            result.put("status",  "fired");
            result.put("eventId", event.getEventId());
            result.put("firedAt", event.getTimestamp().toString());
            result.put("eventType", event.getEventType());
            result.put("source",  source.name());

            return Response.accepted().entity(result).build();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to fire event", e);
            return error(500, "Internal error: " + e.getMessage());
        }
    }

    private Response error(int status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", status);
        return Response.status(status).entity(body).build();
    }

    // ── Request DTO ───────────────────────────────────────────

    public static class FireEventRequest {
        private String eventType;
        private String source;
        private String correlationId;
        private Map<String, Object> payload;
        private Map<String, Object> context;  // optional: { userId, sessionId, userRole }

        public String getEventType()                    { return eventType; }
        public String getSource()                       { return source; }
        public String getCorrelationId()                { return correlationId; }
        public Map<String, Object> getPayload()         { return payload; }
        public Map<String, Object> getContext()         { return context; }

        public void setEventType(String v)              { this.eventType = v; }
        public void setSource(String v)                 { this.source = v; }
        public void setCorrelationId(String v)          { this.correlationId = v; }
        public void setPayload(Map<String, Object> v)   { this.payload = v; }
        public void setContext(Map<String, Object> v)   { this.context = v; }
    }
}
