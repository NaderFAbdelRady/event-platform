package com.platform.components;

import com.platform.sdk.DataStoreService;
import com.platform.sdk.EventBusService;
import com.platform.sdk.HttpClientService;
import com.platform.sdk.WsGatewayService;

/**
 * Convenience base class for components.
 *
 * AI-generated components should extend this class so they only
 * need to implement: getMetadata(), accept(), execute()
 * and optionally override onLoad() / onUnload().
 *
 * SDK services are available as protected fields after onLoad() is called.
 */
public abstract class AbstractEventComponent implements EventComponent {

    // SDK services — available to subclasses in execute()
    protected EventBusService  eventBus;
    protected DataStoreService dataStore;
    protected HttpClientService httpClient;
    protected WsGatewayService wsGateway;

    // ── SDK Injection (called by engine before onLoad) ────────

    @Override
    public final void injectEventBus(EventBusService eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public final void injectDataStore(DataStoreService dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public final void injectHttpClient(HttpClientService httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public final void injectWsGateway(WsGatewayService wsGateway) {
        this.wsGateway = wsGateway;
    }

    // ── Default lifecycle hooks (override if needed) ──────────

    @Override
    public void onLoad() {
        // default: no-op — override to add initialization logic
    }

    @Override
    public void onUnload() {
        // default: no-op — override to add cleanup logic
    }
}
