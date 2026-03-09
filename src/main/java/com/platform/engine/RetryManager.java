package com.platform.engine;

import com.platform.events.PlatformEvent;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles failed event executions with exponential backoff retry.
 *
 * Strategy:
 *   Attempt 1 failed → wait retryDelayMs        → retry
 *   Attempt 2 failed → wait retryDelayMs * 2    → retry
 *   Attempt 3 failed → wait retryDelayMs * 4    → retry
 *   All retries exhausted → route to Dead Letter Queue
 */
@Stateless
public class RetryManager {

    private static final Logger LOG = Logger.getLogger(RetryManager.class.getName());

    @EJB
    private ComponentRegistry registry;

    @EJB
    private DeadLetterQueue deadLetterQueue;

    /**
     * Schedules a retry for a failed event/component pair.
     * Runs asynchronously so it does not block the EventRouter.
     */
    @Asynchronous
    public void scheduleRetry(ComponentEntry entry, PlatformEvent event, String errorMessage) {
        int maxRetries   = entry.getMaxRetries();
        long delayMs     = entry.getRetryDelayMs();
        int attemptCount = 0;
        String lastError = errorMessage;

        while (attemptCount < maxRetries) {
            attemptCount++;

            try {
                // Exponential backoff
                long waitMs = delayMs * (long) Math.pow(2, attemptCount - 1);
                LOG.info("Retry " + attemptCount + "/" + maxRetries
                        + " for component=" + entry.getName()
                        + " event=" + event.getEventId()
                        + " (waiting " + waitMs + "ms)");

                Thread.sleep(waitMs);

                // Check component is still active before retrying
                ComponentEntry current = registry.get(entry.getName());
                if (current == null || current.getStatus() != ComponentStatus.ACTIVE) {
                    LOG.warning("Component no longer active, abandoning retry: " + entry.getName());
                    return;
                }

                current.getInstance().execute(event);

                // Success
                long timeMs = 0; // simplified for retry
                current.recordSuccess(timeMs);
                LOG.info("Retry succeeded for component=" + entry.getName()
                        + " attempt=" + attemptCount);
                logProcessing(event.getEventId(), entry.getName(),
                        "SUCCESS_AFTER_RETRY", attemptCount, null);
                return;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;

            } catch (Exception e) {
                lastError = e.getMessage();
                LOG.log(Level.WARNING,
                        "Retry " + attemptCount + " failed for component="
                        + entry.getName(), e);
            }
        }

        // All retries exhausted → Dead Letter Queue
        LOG.severe("All retries exhausted for component=" + entry.getName()
                + " event=" + event.getEventId() + ". Routing to DLQ.");

        entry.recordFailure();
        deadLetterQueue.add(entry.getName(), event, lastError, attemptCount);
        logProcessing(event.getEventId(), entry.getName(), "DEAD", attemptCount, lastError);
    }

    private void logProcessing(String eventId, String componentName,
                                String status, int attempts, String error) {
        // TODO: persist to event_processing_log
        LOG.info("[PROCESSING] event=" + eventId + " component=" + componentName
                + " status=" + status + " attempts=" + attempts
                + (error != null ? " error=" + error : ""));
    }
}
