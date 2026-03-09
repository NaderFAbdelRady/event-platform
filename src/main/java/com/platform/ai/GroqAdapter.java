package com.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

/**
 * Calls the Groq API (OpenAI-compatible) to generate component code.
 */
public class GroqAdapter implements AIProviderAdapter {

    private static final Logger LOG = Logger.getLogger(GroqAdapter.class.getName());
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL   = "llama-3.3-70b-versatile";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String generate(String systemPrompt, String userPrompt, String apiKey) {
        try {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", MODEL);
            body.put("max_tokens", 4096);

            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
                throw new RuntimeException("Groq API error: " + response.statusCode());

            JsonNode json = JSON.readTree(response.body());
            return json.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            throw new RuntimeException("Groq generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AIProvider getProvider() { return AIProvider.GROQ; }
}
