package com.platform.engine;

import com.platform.events.PlatformEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ejb.Singleton;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * In-memory Dead Letter Queue.
 * Stores events that exhausted all retry attempts.
 *
 * Dates are stored as ISO strings so Jackson can serialize
 * without needing the JavaTimeModule.
 */
@Singleton
public class DeadLetterQueue {

    private static final Logger LOG = Logger.getLogger(DeadLetterQueue.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Map<String, DLQEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(0);

    public void add(String componentName, PlatformEvent event,
                    String failureReason, int retryCount) {
        String id = String.valueOf(idSequence.incrementAndGet());

        String eventSnapshot;
        try { eventSnapshot = JSON.writeValueAsString(event); }
        catch (Exception e) { eventSnapshot = event.toString(); }

        DLQEntry entry = new DLQEntry(
                id, componentName,
                event.getEventId(), event.getEventType(),
                eventSnapshot, failureReason, retryCount
        );

        entries.put(id, entry);
        LOG.severe("[DLQ] Added: component=" + componentName
                + " event=" + event.getEventId()
                + " reason=" + failureReason);
    }

    public List<DLQEntry> getAll() {
        List<DLQEntry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparing(DLQEntry::getId));
        return list;
    }

    public DLQEntry getById(String id) {
        return entries.get(id);
    }

    public void resolve(String id) {
        DLQEntry entry = entries.get(id);
        if (entry != null) {
            entry.setResolved(true);
            entry.setResolvedAt(LocalDateTime.now().format(FMT));
        }
    }

    public void delete(String id) {
        entries.remove(id);
    }

    public void clearResolved() {
        entries.values().removeIf(DLQEntry::isResolved);
    }

    public int size() {
        return entries.size();
    }

    // ── DLQEntry — all fields are primitives or Strings ───────

    public static class DLQEntry {

        private final String id;
        private final String componentName;
        private final String eventId;
        private final String eventType;
        private final String eventSnapshot;
        private final String failureReason;
        private final int    retryCount;
        private final String occurredAt;          // ISO string, not LocalDateTime
        private volatile boolean resolved = false;
        private volatile String resolvedAt = null; // ISO string, not LocalDateTime

        public DLQEntry(String id, String componentName, String eventId,
                        String eventType, String eventSnapshot,
                        String failureReason, int retryCount) {
            this.id            = id;
            this.componentName = componentName;
            this.eventId       = eventId;
            this.eventType     = eventType;
            this.eventSnapshot = eventSnapshot;
            this.failureReason = failureReason;
            this.retryCount    = retryCount;
            this.occurredAt    = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        // Getters — all return String/int/boolean, Jackson-safe
        public String getId()            { return id; }
        public String getComponentName() { return componentName; }
        public String getEventId()       { return eventId; }
        public String getEventType()     { return eventType; }
        public String getEventSnapshot() { return eventSnapshot; }
        public String getFailureReason() { return failureReason; }
        public int    getRetryCount()    { return retryCount; }
        public String getOccurredAt()    { return occurredAt; }
        public boolean isResolved()      { return resolved; }
        public String getResolvedAt()    { return resolvedAt; }

        public void setResolved(boolean resolved)    { this.resolved = resolved; }
        public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }
    }
}
