package com.platform.engine;

import com.platform.events.PlatformEvent;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.enterprise.event.ObservesAsync;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The core event dispatcher.
 *
 * Observes every PlatformEvent fired on the CDI bus,
 * then fans it out to all ACTIVE components in parallel.
 * Each component's accept() is checked first — only matching
 * components have their execute() called.
 *
 * Components in RELOADING state receive events in their queue
 * instead of being executed directly.
 */
@Stateless
public class EventRouter {

    private static final Logger LOG = Logger.getLogger(EventRouter.class.getName());

    // Thread pool for parallel component execution
    // Each component runs in its own thread so no component blocks another
    private static final ExecutorService EXECUTOR =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "component-executor");
                t.setDaemon(true);
                return t;
            });

    @EJB
    private ComponentRegistry registry;

    @EJB
    private RetryManager retryManager;

    /**
     * Entry point — called by CDI whenever a PlatformEvent is fired.
     * @ObservesAsync ensures the REST/WebSocket caller is not blocked.
     */
    public void onEvent(@ObservesAsync PlatformEvent event) {
        logEvent(event);

        Collection<ComponentEntry> components = registry.getAll();

        for (ComponentEntry entry : components) {

            // Queue events for components being reloaded
            if (entry.getStatus() == ComponentStatus.RELOADING) {
                entry.enqueueEvent(event);
                continue;
            }

            // Skip non-active components
            if (entry.getStatus() != ComponentStatus.ACTIVE) {
                continue;
            }

            // Submit each component to the thread pool for parallel execution
            EXECUTOR.submit(() -> dispatch(entry, event));
        }
    }

    private void dispatch(ComponentEntry entry, PlatformEvent event) {
        try {
            // Fast filter — call accept() first
            boolean accepts = entry.getInstance().accept(event);
            if (!accepts) return;

            // Execute with timing
            long start = System.currentTimeMillis();
            entry.getInstance().execute(event);
            long elapsed = System.currentTimeMillis() - start;

            entry.recordSuccess(elapsed);
            logProcessing(event.getEventId(), entry.getName(), "SUCCESS", elapsed, null);

        } catch (Exception e) {
            entry.recordFailure();
            LOG.log(Level.WARNING,
                    "Component " + entry.getName() + " failed on event " + event.getEventId(), e);

            // Hand off to retry manager
            retryManager.scheduleRetry(entry, event, e.getMessage());
        }
    }

    private void logEvent(PlatformEvent event) {
        // TODO: persist to event_log table
        LOG.info("[EVENT] " + event.getEventType() + " | id=" + event.getEventId()
                + " | source=" + event.getSource()
                + " | user=" + (event.getContext() != null ? event.getContext().getUserId() : "-"));
    }

    private void logProcessing(String eventId, String componentName,
                                String status, long timeMs, String error) {
        // TODO: persist to event_processing_log table
        LOG.info("[PROCESSING] event=" + eventId + " component=" + componentName
                + " status=" + status + " time=" + timeMs + "ms"
                + (error != null ? " error=" + error : ""));
    }
}
