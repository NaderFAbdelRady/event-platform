package com.platform.events;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The universal event object that flows through the platform.
 *
 * Structure:
 *   ENVELOPE  — fixed fields present on every event
 *   CONTEXT   — user/session info for filtering
 *   PAYLOAD   — flexible Map<String, Object> body, any data the source wants to carry
 */
public class PlatformEvent implements Serializable {

    // ── Envelope ─────────────────────────────────────────────

    /** Unique identifier for this event */
    private String eventId;

    /** What kind of event this is — e.g. "USER_MESSAGE", "USER_LOGIN" */
    private String eventType;

    /** Where this event originated */
    private EventSource source;

    /** Specific identifier of the source — component name, endpoint path, scheduler name */
    private String sourceId;

    /** When this event was created */
    private LocalDateTime timestamp;

    /**
     * Links this event to a parent event.
     * If ComponentA fires an event in response to EventX,
     * the new event carries EventX's eventId as its correlationId.
     * This enables full chain tracing.
     */
    private String correlationId;

    // ── Context ───────────────────────────────────────────────

    private EventContext context;

    // ── Payload ───────────────────────────────────────────────

    /** Flexible body — any key/value data the source wants to carry */
    private Map<String, Object> payload;

    // ── Constructors ──────────────────────────────────────────

    public PlatformEvent() {
        this.eventId   = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.payload   = new HashMap<>();
        this.context   = EventContext.system();
    }

    // ── Builder-style factory methods ─────────────────────────

    public static PlatformEvent create(String eventType, EventSource source) {
        PlatformEvent e = new PlatformEvent();
        e.eventType = eventType;
        e.source    = source;
        e.sourceId  = source.name();
        return e;
    }

    public PlatformEvent withSourceId(String sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    public PlatformEvent withContext(EventContext context) {
        this.context = context;
        return this;
    }

    public PlatformEvent withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    public PlatformEvent withPayload(Map<String, Object> payload) {
        this.payload = payload;
        return this;
    }

    public PlatformEvent addPayload(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }

    /**
     * Creates a child event that is correlated to this event.
     * Used when a component fires a new event in response to receiving one.
     */
    public PlatformEvent createChildEvent(String eventType, EventSource source, String sourceId) {
        return PlatformEvent.create(eventType, source)
                .withSourceId(sourceId)
                .withCorrelationId(this.eventId)
                .withContext(this.context);
    }

    // ── Payload helpers ───────────────────────────────────────

    public Object getPayloadValue(String key) {
        return payload.get(key);
    }

    public String getPayloadString(String key) {
        Object val = payload.get(key);
        return val != null ? val.toString() : null;
    }

    public boolean hasPayloadKey(String key) {
        return payload.containsKey(key);
    }

    // ── Getters / Setters ─────────────────────────────────────

    public String getEventId()          { return eventId; }
    public String getEventType()        { return eventType; }
    public EventSource getSource()      { return source; }
    public String getSourceId()         { return sourceId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getCorrelationId()    { return correlationId; }
    public EventContext getContext()    { return context; }
    public Map<String, Object> getPayload() { return payload; }

    public void setEventId(String eventId)             { this.eventId = eventId; }
    public void setEventType(String eventType)         { this.eventType = eventType; }
    public void setSource(EventSource source)          { this.source = source; }
    public void setSourceId(String sourceId)           { this.sourceId = sourceId; }
    public void setTimestamp(LocalDateTime timestamp)  { this.timestamp = timestamp; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setContext(EventContext context)        { this.context = context; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "PlatformEvent{eventId='" + eventId + "', type='" + eventType
                + "', source=" + source + ", timestamp=" + timestamp + "}";
    }
}
