package com.platform.ai;

/**
 * Contract every AI provider adapter must implement.
 * Hides provider-specific HTTP calls behind a single method.
 */
public interface AIProviderAdapter {

    /**
     * Sends a prompt to the AI provider and returns the raw text response.
     */
    String generate(String systemPrompt, String userPrompt, String apiKey);

    /**
     * Returns the provider this adapter handles.
     */
    AIProvider getProvider();
}
