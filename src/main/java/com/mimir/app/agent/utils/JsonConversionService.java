package com.mimir.app.agent.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Service for JSON operations using Jackson ObjectMapper.
 * Provides type-safe JSON parsing and conversion methods.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonConversionService {

    private final ObjectMapper objectMapper;

    public String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", e.getMessage());
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to {}: {}", type.getSimpleName(), e.getMessage());
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toMap(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON to Map: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public JsonNode toJsonNode(Object object) {
        return objectMapper.valueToTree(object);
    }

    public <T> T convertValue(JsonNode node, Class<T> type) {
        return objectMapper.convertValue(node, type);
    }

    public List<JsonNode> extractItems(ArrayNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<JsonNode> extractItemsFromSearchResult(Map<String, Object> searchResult) {
        Object itemsObj = searchResult.get("items");
        
        return switch (itemsObj) {
            case ArrayNode arrayNode -> extractItems(arrayNode);
            case List<?> list -> list.stream()
                    .map(this::toJsonNode)
                    .toList();
            case null, default -> {
                log.warn("Could not extract items from search result");
                yield Collections.emptyList();
            }
        };
    }

    public Optional<String> getStringValue(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return Optional.of(node.get(fieldName).asText());
        }
        return Optional.empty();
    }

    public long getLongValue(JsonNode node, String fieldName, long defaultValue) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asLong();
        }
        return defaultValue;
    }
}