package com.platform.service;

import com.platform.sdk.HttpClientService;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ejb.Stateless;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.logging.Logger;

@Stateless
public class HttpClientServiceImpl implements HttpClientService {

    private static final Logger LOG = Logger.getLogger(HttpClientServiceImpl.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Override
    public String get(String url, Map<String, String> headers) {
        try {
            var builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
            if (headers != null) headers.forEach(builder::header);

            HttpResponse<String> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            LOG.warning("HTTP GET failed: " + url + " — " + e.getMessage());
            throw new RuntimeException("HTTP GET failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String post(String url, Object body, Map<String, String> headers) {
        try {
            String bodyJson = JSON.writeValueAsString(body);
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));
            if (headers != null) headers.forEach(builder::header);

            HttpResponse<String> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            LOG.warning("HTTP POST failed: " + url + " — " + e.getMessage());
            throw new RuntimeException("HTTP POST failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String put(String url, Object body, Map<String, String> headers) {
        try {
            String bodyJson = JSON.writeValueAsString(body);
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(bodyJson));
            if (headers != null) headers.forEach(builder::header);

            HttpResponse<String> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("HTTP PUT failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int delete(String url, Map<String, String> headers) {
        try {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE();
            if (headers != null) headers.forEach(builder::header);

            HttpResponse<String> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());

            return response.statusCode();
        } catch (Exception e) {
            throw new RuntimeException("HTTP DELETE failed: " + e.getMessage(), e);
        }
    }
}