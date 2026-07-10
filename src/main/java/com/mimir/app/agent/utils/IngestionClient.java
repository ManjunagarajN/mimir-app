package com.mimir.app.agent.utils;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.agent.config.AgentConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentConfig config;
    private final HttpLogUtil httpLogUtil;

    /**
     * 🆕 CHANGED: Accepts Map<String, byte[]> to preserve original filenames
     */
    public Map<String, Object> ingestPdfFiles(Map<String, byte[]> pdfFiles, String inputSourceData) {
        String url = "http://localhost:8080/api/v1/mimir/ingest";
        log.info("📤 Ingestion URL: {}", url);

        try {
            String boundary = UUID.randomUUID().toString();
            byte[] multipartBody = buildMultipartBody(pdfFiles, inputSourceData, boundary);

            log.info(
                    "📦 Sending {} PDF file(s) to ingestion API (total size: {} bytes)",
                    pdfFiles.size(),
                    multipartBody.length);

            httpLogUtil.logRequest(
                    "POST",
                    url,
                    "Content-Type: multipart/form-data; boundary=" + boundary,
                    String.format("[%d PDF files, inputSourceData=%s]", pdfFiles.size(), inputSourceData));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            String responseBody;
            try (var body = response.body()) {
                responseBody = new String(body.readAllBytes());
            }

            httpLogUtil.logResponse(response.statusCode(), responseBody);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("❌ Ingestion failed with status {}: {}", response.statusCode(), responseBody);
                return Map.of(
                        "success",
                        false,
                        "message",
                        "Ingestion failed with status: " + response.statusCode(),
                        "response",
                        parseResponse(responseBody));
            }

            Object parsedResponse = parseResponse(responseBody);
            boolean isPlainTextAck = parsedResponse instanceof String;

            log.info("✅ Ingestion accepted: {}", responseBody);
            if (isPlainTextAck) {
                log.info(
                        "ℹ️ Ingestion API returned a plain-text acknowledgement (not JSON): \"{}\". "
                                + "This confirms the upload was received, but NOT that any records were "
                                + "actually saved - check the ingestion service's own logs for "
                                + "'savedFiles=0' or 'Parser returned no records' to be sure.",
                        responseBody.trim());
            }

            return Map.of(
                    "success",
                    true,
                    "message",
                    "PDF files ingested successfully",
                    "response",
                    parsedResponse,
                    "responseWasJson",
                    !isPlainTextAck);

        } catch (Exception e) {
            log.error("❌ Error calling ingestion API: {}", e.getMessage(), e);
            return Map.of("success", false, "message", "Ingestion error: " + e.getMessage());
        }
    }

    /**
     * 🆕 CHANGED: Iterates over Map entries to use original filenames
     */
    private byte[] buildMultipartBody(Map<String, byte[]> pdfFiles, String inputSourceData, String boundary)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        for (Map.Entry<String, byte[]> entry : pdfFiles.entrySet()) {
            String fileName = entry.getKey(); // 🆕 Use original filename
            byte[] pdfBytes = entry.getValue();

            baos.write((twoHyphens + boundary + lineEnd).getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + fileName + "\"" + lineEnd)
                    .getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Type: application/pdf" + lineEnd).getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Length: " + pdfBytes.length + lineEnd).getBytes(StandardCharsets.UTF_8));
            baos.write(lineEnd.getBytes(StandardCharsets.UTF_8));

            baos.write(pdfBytes);
            baos.write(lineEnd.getBytes(StandardCharsets.UTF_8));
        }

        if (inputSourceData != null && !inputSourceData.isBlank()) {
            baos.write((twoHyphens + boundary + lineEnd).getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"inputSourceData\"" + lineEnd)
                    .getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Type: text/plain; charset=UTF-8" + lineEnd).getBytes(StandardCharsets.UTF_8));
            baos.write(lineEnd.getBytes(StandardCharsets.UTF_8));
            baos.write(inputSourceData.getBytes(StandardCharsets.UTF_8));
            baos.write(lineEnd.getBytes(StandardCharsets.UTF_8));
        }

        baos.write((twoHyphens + boundary + twoHyphens + lineEnd).getBytes(StandardCharsets.UTF_8));

        return baos.toByteArray();
    }

    /**
     * Parse the response body: if it looks like JSON, return it as a Map/JsonNode-backed
     * structure; otherwise return the raw trimmed String as-is.
     */
    private Object parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Map.of();
        }
        String trimmed = responseBody.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                return objectMapper.convertValue(node, Map.class);
            } catch (Exception e) {
                log.debug("Body looked like JSON but failed to parse: {}", e.getMessage());
                return trimmed;
            }
        }
        return trimmed;
    }
}
