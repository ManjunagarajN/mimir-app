package com.mimir.app.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.domain.RetrievedChunk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class CohereRerankerClient implements RerankerClient {

    private static final String API_KEY = "M77GEL5nIPpZSq5jg8uit4Wbl11lgFEmcHMhvMfJ";
    private static final String URL = "https://api.cohere.com/v2/rerank";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Override
    public List<Float> score(String query, List<RetrievedChunk> chunks) {

        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        try {

            List<String> documents =
                    chunks.stream().map(RetrievedChunk::getContent).toList();

            Map<String, Object> payload = Map.of(
                    "model", "rerank-v4.0-pro", "query", query, "documents", documents, "top_n", documents.size());

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("Calling Cohere rerank API");

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Cohere status={}", response.statusCode());
            log.info("Cohere response={}", response.body());

            if (response.statusCode() != 200) {
                return fallback(chunks);
            }

            return parseScores(response.body(), chunks.size());

        } catch (Exception e) {
            log.error("Cohere rerank failed", e);
            return fallback(chunks);
        }
    }

    private List<Float> parseScores(String json, int size) throws Exception {

        JsonNode root = objectMapper.readTree(json);

        float[] scores = new float[size];

        for (JsonNode node : root.get("results")) {

            int index = node.get("index").asInt();

            float score = (float) node.get("relevance_score").asDouble();

            scores[index] = score;
        }

        List<Float> result = new ArrayList<>();

        for (float score : scores) {
            result.add(score);
        }

        return result;
    }

    private List<Float> fallback(List<RetrievedChunk> chunks) {
        return chunks.stream().map(RetrievedChunk::getScore).toList();
    }
}
