# ⚡ Event Platform

A dynamic event-driven component platform built with Java EE / Jakarta EE.
Components are defined at runtime, AI-generated, and hot-loaded without server restart.

---

## Architecture

```
Event Sources (Frontend / Scheduler / External / Components)
        │
        ▼
CDI Event Bus (@ObservesAsync)
        │
EventRouter → fans out to all ACTIVE components in parallel
        │
ComponentRegistry (in-memory, ConcurrentHashMap)
  each component:
    accept(event) → boolean   (filter)
    execute(event) → void     (function)
        │
RetryManager → exponential backoff → DeadLetterQueue
```

---

## Technology Stack

| Layer           | Technology                          |
|----------------|-------------------------------------|
| App Server      | WildFly 26+ or Payara 5             |
| REST API        | JAX-RS 2.1                          |
| WebSocket       | JSR-356                             |
| Event Bus       | CDI 2.0 `Event<T>` + `@ObservesAsync`|
| Business Logic  | EJB 3.2 `@Stateless` / `@Singleton` |
| Persistence     | JPA 2.2 + Hibernate + MySQL 8       |
| AI Integration  | Claude / Groq / Gemini APIs         |
| Build           | Maven                               |

---

## Project Structure

```
src/main/java/com/platform/
├── events/          PlatformEvent, EventContext, EventSource
├── components/      EventComponent (interface), AbstractEventComponent, ComponentMetadata
├── sdk/             EventBusService, DataStoreService, HttpClientService, WsGatewayService
├── engine/          ComponentLoader, ComponentRegistry, LifecycleManager, EventRouter,
│                    RetryManager, DeadLetterQueue, ComponentStatus
├── ai/              AIGateway, PromptBuilder, ClaudeAdapter, GroqAdapter, GeminiAdapter
├── api/             PlatformApplication, EventResource, ComponentModelerResource
├── websocket/       PlatformWebSocketEndpoint, WebSocketSessionRegistry
└── service/         WsGatewayServiceImpl

src/main/webapp/modeler/    Built-in Component Modeler UI
src/main/resources/META-INF/persistence.xml
```

---

## Setup

### 1. MySQL

```sql
CREATE DATABASE event_platform CHARACTER SET utf8mb4;
CREATE USER 'platform'@'localhost' IDENTIFIED BY 'yourpassword';
GRANT ALL ON event_platform.* TO 'platform'@'localhost';
```

### 2. WildFly DataSource

Add to `standalone.xml`:
```xml
<datasource jndi-name="java:/jdbc/PlatformDS" pool-name="PlatformDS">
    <connection-url>jdbc:mysql://localhost:3306/event_platform</connection-url>
    <driver>mysql</driver>
    <security>
        <user-name>platform</user-name>
        <password>yourpassword</password>
    </security>
</datasource>
```

### 3. Components Directory

```bash
mkdir ~/platform-components
```

### 4. Build and Deploy

```bash
mvn clean package
cp target/event-platform.war $WILDFLY_HOME/standalone/deployments/
```

---

## Component Modeler UI

Open: `http://localhost:8080/modeler/`

---

## Creating a Component

### Option A — AI Generation (via Modeler UI)
1. Open the Modeler UI
2. Click **+ New Component**
3. Enter a name, version, and plain-text description
4. Select your AI provider and enter your API key
5. Click **Generate Class**
6. The component is compiled and live automatically

### Option B — Manual
Create a Java class in `~/platform-components/`:

```java
package com.platform.components;

import com.platform.events.PlatformEvent;

public class MyComponent extends AbstractEventComponent {

    @Override
    public ComponentMetadata getMetadata() {
        return ComponentMetadata.builder()
                .name("MyComponent")
                .version("1.0.0")
                .description("My custom component")
                .build();
    }

    @Override
    public boolean accept(PlatformEvent event) {
        return "USER_MESSAGE".equals(event.getEventType());
    }

    @Override
    public void execute(PlatformEvent event) {
        String userId  = event.getContext().getUserId();
        String content = event.getPayloadString("content");

        // Save to data store
        dataStore.save("messages", Map.of(
            "userId", userId,
            "content", content,
            "timestamp", event.getTimestamp().toString()
        ));

        // Push confirmation back to user
        wsGateway.sendToUser(userId, Map.of(
            "type", "MESSAGE_SAVED",
            "ok", true
        ));
    }
}
```

Drop the `.java` file into `~/platform-components/` — the file watcher compiles and loads it automatically.

---

## Firing Events

### Via REST
```bash
curl -X POST http://localhost:8080/api/events/fire \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_MESSAGE",
    "source": "EXTERNAL",
    "payload": { "content": "Hello", "userId": "user-123" }
  }'
```

### Via WebSocket
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/platform/user-123');
ws.send(JSON.stringify({
  eventType: "USER_MESSAGE",
  content: "Hello from browser"
}));
```

---

## Modeler REST API Reference

| Method | Path | Description |
|--------|------|-------------|
| GET  | /api/modeler/engine/status | Engine health |
| GET  | /api/modeler/engine/registry | All loaded components |
| POST | /api/modeler/engine/components/{name}/reload | Reload component |
| POST | /api/modeler/engine/components/{name}/unload | Unload component |
| POST | /api/modeler/engine/components/{name}/activate | Activate component |
| POST | /api/modeler/components/generate | AI code generation |
| GET  | /api/modeler/dlq | Dead letter queue |
| POST | /api/modeler/dlq/{id}/resolve | Resolve DLQ entry |
| GET  | /api/modeler/metrics/overview | Platform metrics |
| GET  | /api/modeler/metrics/components | Per-component metrics |
| POST | /api/events/fire | Fire an event |
