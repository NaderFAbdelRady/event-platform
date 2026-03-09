package com.platform.sdk;

import com.platform.events.PlatformEvent;

/**
 * SDK Service — allows a component to fire new events back onto the bus.
 * Injected by the engine into every component at load time.
 */
public interface EventBusService {

    /**
     * Fires an event onto the platform event bus.
     * If the event has a correlationId set, the full chain is traceable.
     */
    void fireEvent(PlatformEvent event);
}
