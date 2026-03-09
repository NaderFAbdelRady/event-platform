package com.platform.ai;

import javax.ejb.Stateless;

/**
 * Builds the full prompt sent to the AI provider for component generation.
 *
 * Prompt structure:
 *   SYSTEM — fixed platform context, interface definition, SDK, rules
 *   USER   — component name, version, description (from modeler)
 */
@Stateless
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a Java code generator for an event-driven platform.

            === PLATFORM OVERVIEW ===
            This platform receives events from multiple sources and routes them
            to components. Each component decides which events it handles (via accept())
            and what it does with them (via execute()).

            === EVENT STRUCTURE ===
            The event class is: com.platform.events.PlatformEvent
            It has these methods:
              String getEventId()
              String getEventType()
              EventSource getSource()      // FRONTEND, COMPONENT, SCHEDULER, EXTERNAL
              String getSourceId()
              LocalDateTime getTimestamp()
              String getCorrelationId()
              EventContext getContext()    // .getUserId(), .getSessionId(), .getUserRole()
              Map<String,Object> getPayload()
              Object getPayloadValue(String key)
              String getPayloadString(String key)
              boolean hasPayloadKey(String key)
              PlatformEvent createChildEvent(String eventType, EventSource source, String sourceId)

            === COMPONENT CONTRACT ===
            Your generated class MUST extend AbstractEventComponent:
              package: com.platform.components
              import com.platform.components.AbstractEventComponent;
              import com.platform.components.ComponentMetadata;
              import com.platform.events.PlatformEvent;
              import com.platform.events.EventSource;

            Required methods:
              public ComponentMetadata getMetadata()   // return name, version, description
              public boolean accept(PlatformEvent event)  // filter logic
              public void execute(PlatformEvent event)    // business logic

            Optional overrides:
              public void onLoad()     // initialization
              public void onUnload()   // cleanup

            === SDK SERVICES (available as protected fields) ===
            After extending AbstractEventComponent, these are available in execute():

              eventBus.fireEvent(PlatformEvent event)
                → fires a new event onto the bus

              dataStore.save(String collection, Map<String,Object> data) → String id
              dataStore.find(String collection, Map<String,Object> filters) → List<Map>
              dataStore.findById(String collection, String id) → Map
              dataStore.update(String collection, String id, Map<String,Object> data)
              dataStore.delete(String collection, String id)
              dataStore.count(String collection, Map<String,Object> filters) → long

              httpClient.get(String url, Map<String,String> headers) → String
              httpClient.post(String url, Object body, Map<String,String> headers) → String

              wsGateway.sendToUser(String userId, Object payload)
              wsGateway.broadcast(Object payload)
              wsGateway.isUserConnected(String userId) → boolean

            === STRICT RULES ===
            1. The class MUST be in package com.platform.components
            2. The class MUST extend AbstractEventComponent
            3. Use ONLY the SDK services listed above — no Spring, no external libraries
            4. Allowed imports: java.*, java.util.*, com.platform.* only
            5. No hardcoded credentials, URLs, or passwords
            6. accept() must be fast and side-effect free
            7. Return ONLY raw Java source code — no markdown, no explanation, no code fences
            8. The class must compile cleanly with Java 11
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String buildUserPrompt(String name, String version, String description) {
        return """
                Generate a complete Java class with the following specification:

                Component Name:    %s
                Component Version: %s
                Description:
                %s

                Requirements:
                - Class name must be exactly: %s
                - Extend AbstractEventComponent
                - Implement getMetadata(), accept(), execute()
                - Use only the SDK services described above

                Return ONLY the raw Java source code.
                """.formatted(name, version, description, name);
    }

    public String buildRetryPrompt(String name, String version, String description,
                                   String previousAttempt, String errorMessage) {
        return """
                Your previous attempt to generate the component failed with this error:

                ERROR:
                %s

                PREVIOUS CODE:
                %s

                Please fix the issue and regenerate the complete class.

                Component Name:    %s
                Component Version: %s
                Description:
                %s

                Return ONLY the corrected raw Java source code.
                """.formatted(errorMessage, previousAttempt, name, version, description);
    }
}
