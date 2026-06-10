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

class AnthropicKeySmokeTest {

    //If you are running outside company enable it
    @Disabled
    @Test
    void shouldValidateAnthropicApiKeyWithSimpleClaudeCall() throws IOException, InterruptedException {
        // Load API key from application-local.yaml on the classpath.
        String apiKey = TestKeyLoader.loadKey("anthropic");
        assertTrue(apiKey != null && !apiKey.isBlank(), "Anthropic key must be set in application-local.yaml under app.keys.anthropic");

        String payload = """
                {
                  "model": "claude-3-5-haiku-latest",
                  "max_tokens": 16,
                  "messages": [
                    {"role": "user", "content": "Reply only with OK"}
                  ]
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "Anthropic key check failed. Response: " + response.body());
        assertTrue(response.body().contains("\"content\""), "Unexpected Anthropic response: " + response.body());
    }
}

