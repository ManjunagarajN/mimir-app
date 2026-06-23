package com.mimir.app.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Low-level HTTP streaming client for Ollama's /api/chat endpoint.
 * Used exclusively by ChatGrpcService (the gRPC server side).
 */
@Component
public class OllamaStreamClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaStreamClient.class);

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.temperature:0.3}")
    private float temperature;

    @Value("${ollama.max-tokens:2048}")
    private int maxTokens;

    @Value("${ollama.timeout-seconds:120}")
    private int timeoutSeconds;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OllamaStreamClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.mapper = new ObjectMapper();
    }

    public void stream(String model, String prompt, Consumer<String> tokenConsumer, AtomicBoolean cancelled)
            throws IOException, InterruptedException {

        var payload = Map.of(
                "model",
                model,
                "messages",
                List.of(Map.of("role", "user", "content", prompt)),
                "stream",
                true,
                "options",
                Map.of(
                        "temperature", temperature,
                        "num_predict", maxTokens));

        String jsonBody = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String error = new String(response.body().readAllBytes());
            log.error("Ollama HTTP error: {}", error);
            throw new IOException("Ollama returned HTTP " + response.statusCode());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (cancelled.get()) {
                    log.debug("Stream cancelled | model={}", model);
                    return;
                }

                if (line.isBlank()) continue;

                JsonNode node = mapper.readTree(line);

                // ✅ Ollama sends incremental tokens directly — NOT cumulative
                String delta = node.path("message").path("content").asText();

                if (!delta.isEmpty()) {
                    tokenConsumer.accept(delta);
                }

                if (node.path("done").asBoolean(false)) break;
            }
        }
    }
}
