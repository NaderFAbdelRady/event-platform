package com.platform.engine;

import com.platform.components.EventComponent;
import com.platform.events.PlatformEvent;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages component lifecycle state transitions:
 *   LOADING → ACTIVE
 *   ACTIVE  → RELOADING → ACTIVE (new version)
 *   ACTIVE  → UNLOADED
 *   any     → FAILED
 *
 * Ensures in-flight events are queued during reload
 * and drained after the new version is active.
 */
@Stateless
public class LifecycleManager {

    private static final Logger LOG = Logger.getLogger(LifecycleManager.class.getName());

    @EJB
    private ComponentRegistry registry;

    /**
     * Loads a new component or reloads an existing one.
     * If the component already exists (reload), queues events during transition.
     */
    public void load(EventComponent component, ClassLoader classLoader) {
        String name = component.getMetadata().getName();

        ComponentEntry existing = registry.get(name);

        if (existing != null) {
            reload(existing, component, classLoader);
        } else {
            loadFresh(name, component, classLoader);
        }
    }

    private void loadFresh(String name, EventComponent component, ClassLoader classLoader) {
        ComponentEntry entry = new ComponentEntry(name, component, classLoader);
        entry.setStatus(ComponentStatus.LOADING);
        registry.register(entry);

        try {
            component.onLoad();
            entry.setStatus(ComponentStatus.ACTIVE);
            entry.setLoadedAt(LocalDateTime.now());
            LOG.info("Component LOADED: " + name);
            logLifecycleEvent(name, "LOADED", null);

        } catch (Exception e) {
            entry.setStatus(ComponentStatus.FAILED);
            entry.setLastError(e.getMessage());
            LOG.log(Level.SEVERE, "Component FAILED to load: " + name, e);
            logLifecycleEvent(name, "FAILED", e.getMessage());
        }
    }

    private void reload(ComponentEntry existing, EventComponent newComponent, ClassLoader newClassLoader) {
        String name = existing.getName();
        LOG.info("Component RELOADING: " + name);

        // Set to RELOADING — EventRouter will queue events for this component
        existing.setStatus(ComponentStatus.RELOADING);
        logLifecycleEvent(name, "RELOADING", null);

        try {
            // Unload old instance
            try {
                existing.getInstance().onUnload();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "onUnload() threw exception for: " + name, e);
            }

            // Close old ClassLoader
            if (existing.getClassLoader() instanceof java.net.URLClassLoader) {
                try { ((java.net.URLClassLoader) existing.getClassLoader()).close(); }
                catch (Exception ignored) {}
            }

            // Load new instance
            newComponent.onLoad();

            // Swap in new instance atomically
            existing.setInstance(newComponent);
            existing.setClassLoader(newClassLoader);
            existing.setLoadedAt(LocalDateTime.now());
            existing.setLastError(null);
            existing.setStatus(ComponentStatus.ACTIVE);

            LOG.info("Component RELOADED: " + name);
            logLifecycleEvent(name, "RELOADED", null);

            // Drain queued events
            drainQueue(existing);

        } catch (Exception e) {
            existing.setStatus(ComponentStatus.FAILED);
            existing.setLastError(e.getMessage());
            LOG.log(Level.SEVERE, "Component FAILED to reload: " + name, e);
            logLifecycleEvent(name, "FAILED", e.getMessage());
        }
    }

    /**
     * Manually unloads a component from the registry.
     * Component definition remains in the DB.
     */
    public void unload(String name) {
        ComponentEntry entry = registry.get(name);
        if (entry == null) {
            LOG.warning("Unload requested for unknown component: " + name);
            return;
        }

        try {
            entry.getInstance().onUnload();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "onUnload() threw exception for: " + name, e);
        }

        entry.setStatus(ComponentStatus.UNLOADED);
        LOG.info("Component UNLOADED: " + name);
        logLifecycleEvent(name, "UNLOADED", null);
    }

    /**
     * Activates a previously unloaded component.
     */
    public void activate(String name) {
        ComponentEntry entry = registry.get(name);
        if (entry == null) {
            LOG.warning("Activate requested for unknown component: " + name);
            return;
        }

        try {
            entry.getInstance().onLoad();
            entry.setStatus(ComponentStatus.ACTIVE);
            LOG.info("Component ACTIVATED: " + name);
            logLifecycleEvent(name, "LOADED", null);
        } catch (Exception e) {
            entry.setStatus(ComponentStatus.FAILED);
            entry.setLastError(e.getMessage());
            logLifecycleEvent(name, "FAILED", e.getMessage());
        }
    }

    // Drain queued events after reload completes
    private void drainQueue(ComponentEntry entry) {
        int count = 0;
        PlatformEvent queued;
        while ((queued = entry.drainQueue().poll()) != null) {
            try {
                if (entry.getInstance().accept(queued)) {
                    entry.getInstance().execute(queued);
                    count++;
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to process queued event after reload", e);
            }
        }
        if (count > 0) LOG.info("Drained " + count + " queued events for: " + entry.getName());
    }

    private void logLifecycleEvent(String componentName, String eventType, String detail) {
        // TODO: persist to component_events table via JPA
        LOG.info("[LIFECYCLE] " + componentName + " → " + eventType
                + (detail != null ? " | " + detail : ""));
    }
}
