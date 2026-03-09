package com.platform.sdk;

import java.util.Map;

/**
 * SDK Service — allows components to make outbound HTTP calls
 * to external APIs or webhooks.
 * Injected by the engine at load time.
 */
public interface HttpClientService {

    /**
     * Performs an HTTP GET request.
     * Returns the response body as a String.
     */
    String get(String url, Map<String, String> headers);

    /**
     * Performs an HTTP POST request with a JSON body.
     * Returns the response body as a String.
     */
    String post(String url, Object body, Map<String, String> headers);

    /**
     * Performs an HTTP PUT request with a JSON body.
     * Returns the response body as a String.
     */
    String put(String url, Object body, Map<String, String> headers);

    /**
     * Performs an HTTP DELETE request.
     * Returns the HTTP status code.
     */
    int delete(String url, Map<String, String> headers);
}
