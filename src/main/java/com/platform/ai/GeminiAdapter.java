package com.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

/**
 * Calls the Google Gemini API to generate component code.
 */
public class GeminiAdapter implements AIProviderAdapter {

    private static final Logger LOG = Logger.getLogger(GeminiAdapter.class.getName());
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String generate(String systemPrompt, String userPrompt, String apiKey) {
        try {
            String combined = systemPrompt + "\n\n" + userPrompt;

            String bodyJson = """
                    {
                      "contents": [{
                        "parts": [{"text": %s}]
                      }],
                      "generationConfig": { "maxOutputTokens": 4096 }
                    }
                    """.formatted(JSON.writeValueAsString(combined));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200)
                throw new RuntimeException("Gemini API error: " + response.statusCode());

            JsonNode json = JSON.readTree(response.body());
            return json.path("candidates").get(0)
                       .path("content").path("parts").get(0)
                       .path("text").asText();

        } catch (Exception e) {
            throw new RuntimeException("Gemini generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AIProvider getProvider() { return AIProvider.GEMINI; }
}
