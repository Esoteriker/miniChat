package com.minichat.api.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minichat.api.common.ConflictException;
import com.minichat.api.common.NotFoundException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class InferenceClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public InferenceClient(ObjectMapper objectMapper,
                           @Value("${app.inference.base-url}") String baseUrl,
                           @Value("${app.inference.connect-timeout-ms}") int connectTimeoutMs) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build();
    }

    public void streamGenerate(GenerateRequest payload, Consumer<JsonNode> onEvent) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(uri("/internal/generate"))
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new ConflictException("Inference stream request failed with status " + response.statusCode());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String json = line.substring(5).trim();
                if (json.isEmpty()) {
                    continue;
                }
                onEvent.accept(objectMapper.readTree(json));
            }
        }
    }

    public void cancelGeneration(UUID generationId) {
        try {
            String body = objectMapper.writeValueAsString(new CancelRequest(generationId.toString()));
            HttpRequest request = HttpRequest.newBuilder(uri("/internal/cancel"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() != 404) {
                throw new NotFoundException("Cancel failed with status " + response.statusCode());
            }
        } catch (Exception ignored) {
            // Best effort cancel for in-flight inference.
        }
    }

    private URI uri(String path) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized + path);
    }

    public record GenerateRequest(
        String generationId,
        String model,
        String systemPrompt,
        Double temperature,
        Integer maxTokens,
        List<GenerateMessage> messages
    ) {
    }

    public record GenerateMessage(String role, String content) {
    }

    public record CancelRequest(String generationId) {
    }
}
