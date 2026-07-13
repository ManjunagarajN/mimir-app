package com.mimir.app.agent.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.agent.config.AgentConfig;
import com.mimir.app.agent.domain.SearchCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseIntentClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentConfig config;
    private final HttpLogUtil httpLogUtil;

    public Map<String, Object> searchPurchaseIntents(String accessToken, SearchCriteria criteria) {
        var url = config.getBaseUrl() + config.getApiVersion() + "/range-plan/search";
        
        try {
            String jsonBody = objectMapper.writeValueAsString(criteria);
            log.info("🔍 Search URL: {}", url);
            httpLogUtil.logRequest("POST", url, "Authorization: Bearer " + httpLogUtil.maskToken(accessToken), jsonBody);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(30)) // 🆕 Add timeout to prevent hanging
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            String responseBody;
            try (var body = response.body()) {
                responseBody = new String(body.readAllBytes());
            }

            httpLogUtil.logResponse(response.statusCode(), responseBody);

            if (response.statusCode() == 200) {
                var root = objectMapper.readTree(responseBody);
                long totalRecord = root.has("totalRecord") ? root.get("totalRecord").asLong() : 0;
                
                return Map.of(
                        "success", true,
                        "data", root.has("data") ? objectMapper.treeToValue(root.get("data"), Object.class) : Map.of(),
                        "totalRecord", totalRecord
                );
            } else {
                return createErrorResponse(new RuntimeException("HTTP " + response.statusCode() + ": " + responseBody), "Search failed");
            }

        } catch (Exception e) {
            // 🆕 Use safe error response handler
            return createErrorResponse(e, "Search error");
        }
    }

    // 🆕 Add this helper method to safely handle null exception messages
    private Map<String, Object> createErrorResponse(Exception e, String context) {
        String errorMessage = e.getMessage();
        
        // Handle null messages (common with ConnectException)
        if (errorMessage == null) {
            errorMessage = e.getClass().getSimpleName();
        }
        
        // Provide specific feedback for connection issues
        if (e instanceof java.net.ConnectException) {
            errorMessage = "Connection refused. Cannot reach backend at " + config.getBaseUrl() + ". Please ensure the Vortx service is running.";
        } else if (e instanceof java.net.http.HttpConnectTimeoutException) {
            errorMessage = "Connection timed out while trying to reach " + config.getBaseUrl();
        }
        
        log.error("❌ {}: {}", context, errorMessage);
        
        // Use HashMap instead of Map.of() to safely handle any remaining nulls
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", errorMessage);
        return response;
    }
}