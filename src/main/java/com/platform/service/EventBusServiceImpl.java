package com.platform.service;

import com.platform.events.PlatformEvent;
import com.platform.sdk.EventBusService;

import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;

@Stateless
public class EventBusServiceImpl implements EventBusService {

    @Inject
    private Event<PlatformEvent> cdiEventBus;

    @Override
    public void fireEvent(PlatformEvent event) {
        cdiEventBus.fireAsync(event);
    }
}