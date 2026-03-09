package com.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

/**
 * Calls the Anthropic Claude API to generate component code.
 */
public class ClaudeAdapter implements AIProviderAdapter {

    private static final Logger LOG = Logger.getLogger(ClaudeAdapter.class.getName());
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-opus-4-6";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String generate(String systemPrompt, String userPrompt, String apiKey) {
        try {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", MODEL);
            body.put("max_tokens", 4096);
            body.put("system", systemPrompt);

            ArrayNode messages = body.putArray("messages");
            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Claude API error: " + response.statusCode()
                        + " " + response.body());
            }

            JsonNode responseJson = JSON.readTree(response.body());
            return responseJson.path("content").get(0).path("text").asText();

        } catch (Exception e) {
            LOG.severe("Claude API call failed: " + e.getMessage());
            throw new RuntimeException("Claude generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AIProvider getProvider() {
        return AIProvider.CLAUDE;
    }
}
