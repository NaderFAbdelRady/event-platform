package com.platform.components;

import com.platform.events.PlatformEvent;
import com.platform.sdk.DataStoreService;
import com.platform.sdk.EventBusService;
import com.platform.sdk.HttpClientService;
import com.platform.sdk.WsGatewayService;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║              THE COMPONENT CONTRACT                         ║
 * ║                                                             ║
 * ║  Every component loaded into the platform — whether        ║
 * ║  hand-written or AI-generated — must implement this        ║
 * ║  interface.                                                 ║
 * ║                                                             ║
 * ║  The engine will:                                           ║
 * ║    1. Call getMetadata() to register the component         ║
 * ║    2. Inject SDK services via the inject* methods          ║
 * ║    3. Call onLoad() once after injection                   ║
 * ║    4. Call accept(event) for every event on the bus        ║
 * ║    5. Call execute(event) only if accept() returns true    ║
 * ║    6. Call onUnload() before removing the component        ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
public interface EventComponent {

    /**
     * Returns identity and descriptive information about this component.
     * Called once by the engine during registration.
     */
    ComponentMetadata getMetadata();

    /**
     * Decides whether this component wants to handle the given event.
     * All filtering logic lives here — by event type, source, payload content,
     * user role, or any combination.
     *
     * Return true  → engine will call execute(event)
     * Return false → engine skips this component for this event
     *
     * Must be fast and side-effect free — it is called for EVERY event.
     */
    boolean accept(PlatformEvent event);

    /**
     * Performs this component's function.
     * Only called when accept() returns true.
     *
     * SDK services are available here:
     *   eventBus   → fire new events
     *   dataStore  → read/write data
     *   httpClient → call external APIs
     *   wsGateway  → push to connected browsers
     */
    void execute(PlatformEvent event);

    /**
     * Called once by the engine after SDK services are injected
     * and before the component starts receiving events.
     * Use for initialization — open connections, load config, warm up caches.
     */
    void onLoad();

    /**
     * Called by the engine before this component is removed or reloaded.
     * Use for cleanup — close connections, flush buffers, release resources.
     */
    void onUnload();

    // ── SDK Injection ─────────────────────────────────────────
    // The engine calls these methods to inject platform services
    // before calling onLoad(). Components store these as fields.

    void injectEventBus(EventBusService eventBus);
    void injectDataStore(DataStoreService dataStore);
    void injectHttpClient(HttpClientService httpClient);
    void injectWsGateway(WsGatewayService wsGateway);
}
