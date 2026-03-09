package com.platform.events;

public enum EventSource {
    FRONTEND,    // fired by a web client via REST or WebSocket
    COMPONENT,   // fired by another component (chained events)
    SCHEDULER,   // fired by a timer/scheduled trigger
    EXTERNAL     // fired by an external system via webhook/API
}
