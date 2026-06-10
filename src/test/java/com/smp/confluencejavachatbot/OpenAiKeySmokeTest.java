package com.smp.confluencejavachatbot;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiKeySmokeTest {

    @Disabled
    @Test
    void shouldValidateOpenAiApiKeyByListingModels() throws IOException, InterruptedException {
        // Load API key from application-local.yaml on the classpath.
        String apiKey = TestKeyLoader.loadKey("openai");
        assertTrue(apiKey != null && !apiKey.isBlank(), "OpenAI key must be set in application-local.yaml under app.keys.openai");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/models"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "OpenAI key check failed. Response: " + response.body());
        assertTrue(response.body().contains("\"data\""), "Unexpected OpenAI response body");
    }
}

