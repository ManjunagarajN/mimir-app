package com.mimir.app.agent.utils;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DRY replacement for the logRequest/logResponse/maskToken methods that were
 * duplicated (byte-for-byte) in AuthClient, PurchaseIntentClient and PdfClient.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpLogUtil {

    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    private final ObjectMapper objectMapper;

    public String maskToken(String token) {
        return token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null";
    }

    public void logRequest(String method, String url, String headers, String body) {
        log.info(SEPARATOR);
        log.info("📤 HTTP {} REQUEST", method);
        log.info("📍 URL: {}", url);
        if (headers != null) {
            log.info("📋 Headers: {}", headers);
        }
        if (body != null && !body.isEmpty()) {
            log.info("📦 Request Body:\n{}", prettyOrRaw(body));
        }
        log.info(SEPARATOR);
    }

    public void logResponse(int statusCode, String body) {
        log.info(SEPARATOR);
        log.info("📥 HTTP RESPONSE");
        log.info("📊 Status: {}", statusCode);
        if (body != null && !body.isEmpty()) {
            log.trace("📦 Response Body:\n{}", prettyOrRaw(body));
        }
        log.info(SEPARATOR);
    }

    private String prettyOrRaw(String body) {
        try {
            var trimmed = body.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                var node = objectMapper.readTree(body);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            }
        } catch (Exception e) {
            log.debug("Could not pretty-print body: {}", e.getMessage());
        }
        return body;
    }
}
