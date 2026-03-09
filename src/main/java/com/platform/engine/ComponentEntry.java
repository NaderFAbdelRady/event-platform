package com.platform.engine;

import com.platform.components.EventComponent;
import com.platform.events.PlatformEvent;

import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds a loaded component and all its runtime state
 * in the ComponentRegistry.
 */
public class ComponentEntry {

    private final String name;
    private volatile EventComponent instance;
    private volatile ComponentStatus status;
    private volatile ClassLoader classLoader;
    private volatile LocalDateTime loadedAt;
    private volatile String lastError;

    // ── Metrics ───────────────────────────────────────────────
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final AtomicLong eventsFailed    = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);

    // ── Event queue used during RELOADING ─────────────────────
    private final Queue<PlatformEvent> eventQueue = new ConcurrentLinkedQueue<>();

    // ── Retry config ──────────────────────────────────────────
    private int maxRetries    = 3;
    private long retryDelayMs = 1000;

    public ComponentEntry(String name, EventComponent instance, ClassLoader classLoader) {
        this.name        = name;
        this.instance    = instance;
        this.classLoader = classLoader;
        this.status      = ComponentStatus.LOADING;
        this.loadedAt    = LocalDateTime.now();
    }

    // ── Metrics helpers ───────────────────────────────────────

    public void recordSuccess(long processingTimeMs) {
        eventsProcessed.incrementAndGet();
        totalProcessingTimeMs.addAndGet(processingTimeMs);
    }

    public void recordFailure() {
        eventsFailed.incrementAndGet();
    }

    public long getAvgProcessingTimeMs() {
        long processed = eventsProcessed.get();
        if (processed == 0) return 0;
        return totalProcessingTimeMs.get() / processed;
    }

    // ── Event queue helpers (used during RELOADING) ───────────

    public void enqueueEvent(PlatformEvent event) {
        eventQueue.offer(event);
    }

    public Queue<PlatformEvent> drainQueue() {
        return eventQueue;
    }

    // ── Getters / Setters ─────────────────────────────────────

    public String getName()                     { return name; }
    public EventComponent getInstance()         { return instance; }
    public ComponentStatus getStatus()          { return status; }
    public ClassLoader getClassLoader()         { return classLoader; }
    public LocalDateTime getLoadedAt()          { return loadedAt; }
    public String getLastError()                { return lastError; }
    public long getEventsProcessed()            { return eventsProcessed.get(); }
    public long getEventsFailed()               { return eventsFailed.get(); }
    public int getMaxRetries()                  { return maxRetries; }
    public long getRetryDelayMs()               { return retryDelayMs; }

    public void setInstance(EventComponent instance)     { this.instance = instance; }
    public void setStatus(ComponentStatus status)        { this.status = status; }
    public void setClassLoader(ClassLoader classLoader)  { this.classLoader = classLoader; }
    public void setLoadedAt(LocalDateTime loadedAt)      { this.loadedAt = loadedAt; }
    public void setLastError(String lastError)           { this.lastError = lastError; }
    public void setMaxRetries(int maxRetries)            { this.maxRetries = maxRetries; }
    public void setRetryDelayMs(long retryDelayMs)       { this.retryDelayMs = retryDelayMs; }
}
