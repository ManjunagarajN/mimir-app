package com.mimir.app.agent.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.agent.config.AgentConfig;
import com.mimir.app.agent.domain.LineItemsFilterRequest;
import com.mimir.app.agent.domain.MaxPurchaseIntentRequest;
import com.mimir.app.agent.domain.SearchCriteria;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseIntentClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentConfig config;
    private final HttpLogUtil httpLogUtil;

    public Map<String, Object> searchPurchaseIntents(String accessToken, SearchCriteria criteria) throws Exception {
        try {
            var filter = buildFilterRequest(criteria);
            var jsonBody = objectMapper.writeValueAsString(filter);
            var url = config.getBaseUrl() + config.getApiVersion() + "/range-plan/search";

            log.info("🔍 Search URL: {}", url);
            httpLogUtil.logRequest(
                    "POST", url, "Authorization: Bearer " + httpLogUtil.maskToken(accessToken), jsonBody);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return handleSearchResponse(response);
        } catch (Exception e) {
            log.error("❌ Search error: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // 🆕 NEW METHOD: Create multiple PIs at once
    public Map<String, Object> createPurchaseIntents(String accessToken, List<MaxPurchaseIntentRequest> requests)
            throws Exception {
        if (requests == null || requests.isEmpty()) {
            return Map.of("success", false, "error", "No requests provided");
        }

        var url = config.getBaseUrl() + config.getApiVersion() + "/purchase-intents";
        var jsonBody = objectMapper.writeValueAsString(requests); // Serializes as a JSON Array [ {...}, {...} ]

        log.info("📦 Creating {} PIs...", requests.size());
        httpLogUtil.logRequest("POST", url, "Authorization: Bearer " + httpLogUtil.maskToken(accessToken), jsonBody);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        String responseBody;
        try (var body = response.body()) {
            responseBody = new String(body.readAllBytes());
        }

        httpLogUtil.logResponse(response.statusCode(), responseBody);

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            log.info("✅ PIs created successfully!");
            return Map.of("success", true, "data", objectMapper.readValue(responseBody, Object.class));
        } else {
            log.error("❌ PI creation failed: {} - {}", response.statusCode(), responseBody);
            return Map.of("success", false, "error", responseBody);
        }
    }

    private LineItemsFilterRequest buildFilterRequest(SearchCriteria criteria) {
        String divName = criteria.getDivName() != null && !criteria.getDivName().isEmpty()
                ? criteria.getDivName().trim()
                : null;

        List<String> groupNames = new ArrayList<>();
        if (criteria.getGroupName() != null) {
            for (String group : criteria.getGroupName()) {
                if (group != null && !group.isEmpty()) {
                    groupNames.add(group.toUpperCase());
                }
            }
        }

        List<String> deptNames = new ArrayList<>();
        if (criteria.getDeptName() != null) {
            for (String dept : criteria.getDeptName()) {
                if (dept != null && !dept.isEmpty()) {
                    deptNames.add(dept.toUpperCase());
                }
            }
        }

        List<String> styleList = null;
        if (criteria.getStyle() != null) {
            styleList = new ArrayList<>();
            for (String style : criteria.getStyle()) {
                if (style != null && !style.isEmpty()) {
                    styleList.add(style.toUpperCase());
                }
            }
        }

        return new LineItemsFilterRequest(
                divName,
                groupNames.isEmpty() ? null : groupNames,
                deptNames.isEmpty() ? null : deptNames,
                criteria.getClassName(),
                criteria.getSubName(),
                criteria.getSizeRange(),
                criteria.getSeason(),
                criteria.getHit(),
                styleList,
                criteria.getVendor(),
                criteria.getLineItemStatus(),
                criteria.getSheetName(),
                criteria.getSourceFile(),
                criteria.getNextPageCursor(),
                criteria.getAfterId(),
                criteria.getAfterStyle(),
                criteria.getFirst());
    }

    private Map<String, Object> handleSearchResponse(HttpResponse<java.io.InputStream> response) throws Exception {
        String responseBody;
        try (var body = response.body()) {
            responseBody = new String(body.readAllBytes());
        }

        httpLogUtil.logResponse(response.statusCode(), responseBody);

        var result = new HashMap<String, Object>();

        if (response.statusCode() == 200) {
            var root = objectMapper.readTree(responseBody);
            if (root.has("data")) {
                var data = root.get("data");
                result.put("success", true);
                result.put("items", data.has("items") ? data.get("items") : data);
                result.put(
                        "totalRecord",
                        data.has("totalRecord") ? data.get("totalRecord").asLong() : 0);
                result.put(
                        "hasNextPage",
                        data.has("hasNextPage") ? data.get("hasNextPage").asBoolean() : false);
                result.put(
                        "nextPageCursor",
                        data.has("nextPageCursor") ? data.get("nextPageCursor").asText() : null);
                log.info("✅ Search completed: {} results found", result.get("totalRecord"));
            } else {
                result.put("success", true);
                result.put("items", objectMapper.readValue(responseBody, List.class));
                result.put("totalRecord", 0);
            }
            return result;
        } else {
            log.error("❌ Search failed: {} - {}", response.statusCode(), responseBody);
            result.put("success", false);
            result.put("error", "API returned " + response.statusCode() + ": " + responseBody);
            return result;
        }
    }

    private Map<String, Object> handleCreateResponse(HttpResponse<java.io.InputStream> response) throws Exception {
        String responseBody;
        try (var body = response.body()) {
            responseBody = new String(body.readAllBytes());
        }

        httpLogUtil.logResponse(response.statusCode(), responseBody);

        var result = new HashMap<String, Object>();

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            var root = objectMapper.readTree(responseBody);

            if (root.has("data")) {
                var data = root.get("data");
                result.put("success", true);
                result.put("data", data);
                result.put("message", "Purchase intent created successfully");

                var piNumbers = extractPiNumbers(data);
                result.put("piNumbers", piNumbers);
                log.info("✅ PI created with numbers: {}", piNumbers);
            } else {
                var responseData = objectMapper.readValue(responseBody, Map.class);
                result.put("success", true);
                result.put("data", responseData);
                result.put("message", "Purchase intent created successfully");
            }
            return result;
        } else {
            log.error("❌ Create failed: {} - {}", response.statusCode(), responseBody);
            result.put("success", false);
            result.put("error", "API returned " + response.statusCode() + ": " + responseBody);
            return result;
        }
    }

    private List<Long> extractPiNumbers(com.fasterxml.jackson.databind.JsonNode data) {
        var piNumbers = new ArrayList<Long>();

        if (data.isArray()) {
            for (var item : data) {
                extractPiFromNode(item, piNumbers);
            }
        } else {
            extractPiFromNode(data, piNumbers);
        }

        return piNumbers;
    }

    private void extractPiFromNode(com.fasterxml.jackson.databind.JsonNode node, List<Long> piNumbers) {
        if (node.has("purchaseIntentNumber")) {
            var piNode = node.get("purchaseIntentNumber");
            if (!piNode.isNull()) {
                try {
                    var pi = piNode.asLong();
                    piNumbers.add(pi);
                    log.info("📊 Extracted PI number from purchaseIntentNumber: {}", pi);
                    return;
                } catch (NumberFormatException e) {
                    log.warn("Could not parse purchaseIntentNumber: {}", piNode);
                }
            }
        }
        if (node.has("purchaseIntentNo")) {
            var piNode = node.get("purchaseIntentNo");
            if (!piNode.isNull()) {
                try {
                    var pi = piNode.asLong();
                    piNumbers.add(pi);
                    log.info("📊 Extracted PI number from purchaseIntentNo: {}", pi);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse purchaseIntentNo: {}", piNode);
                }
            }
        }
    }

    public Map<String, Object> requestAllocation(String accessToken, List<MaxPurchaseIntentRequest> allocationRequests)
            throws Exception {
        if (allocationRequests == null || allocationRequests.isEmpty()) {
            return Map.of("success", false, "error", "No allocation requests provided");
        }

        var url = config.getBaseUrl() + config.getApiVersion() + "/purchase-intents/";
        log.info("📊 Requesting allocation for {} PIs", allocationRequests.size());

        // 🆕 Serialize the FULL request objects (includes maxPurchaseLineItems, identity, sourceSnapshot,
        // workflowCommand)
        var jsonBody = objectMapper.writeValueAsString(allocationRequests);

        httpLogUtil.logRequest("PATCH", url, "Authorization: Bearer " + httpLogUtil.maskToken(accessToken), jsonBody);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        String responseBody;
        try (var body = response.body()) {
            responseBody = new String(body.readAllBytes());
        }

        httpLogUtil.logResponse(response.statusCode(), responseBody);

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("✅ Allocation request successful!");
            return Map.of("success", true, "message", "Allocation requested successfully");
        } else {
            log.error("❌ Allocation request failed: {} - {}", response.statusCode(), responseBody);
            return Map.of("success", false, "error", responseBody);
        }
    }
}
