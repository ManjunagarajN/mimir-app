package com.mimir.app.rag.embedding;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.domain.QueryVector;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OllamaEmbeddingService implements EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String MODEL = "nomic-embed-text";
    private static final String OLLAMA_URL = "http://172.16.13.51:11434/api/embeddings";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public QueryVector embed(String text) {
        try {
            // Request payload
            Map<String, Object> payload = Map.of("model", MODEL, "prompt", text);

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama embedding failed: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddingNode = root.get("embedding");

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }

            return QueryVector.builder()
                    .vector(vector)
                    .dimension(vector.length)
                    .model(MODEL)
                    .createdAt(System.currentTimeMillis())
                    .sourceText(text)
                    .build();

        } catch (Exception e) {
            log.error("Ollama embedding failed", e);
            throw new RuntimeException("Embedding generation failed", e);
        }
    }
}
