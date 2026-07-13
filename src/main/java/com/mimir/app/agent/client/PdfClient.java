package com.mimir.app.agent.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

import com.mimir.app.agent.utils.HttpLogUtil;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.agent.config.AgentConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentConfig config;
    private final HttpLogUtil httpLogUtil;

    public byte[] downloadPurchaseIntentPdf(String accessToken, List<Long> piNumbers) throws Exception {
        if (piNumbers == null || piNumbers.isEmpty()) {
            log.warn("⚠️ No PI numbers provided for PDF generation");
            return new byte[0];
        }

        var piNumbersStr = piNumbers.stream().map(String::valueOf).collect(Collectors.joining(","));

        var url = config.getBaseUrl() + config.getApiVersion() + "/purchase-intents/pdf/" + piNumbersStr;
        log.info("📄 Downloading PDF for PI: {}", piNumbersStr);
        httpLogUtil.logRequest("POST", url, "Authorization: Bearer " + httpLogUtil.maskToken(accessToken), null);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
            var pdfBytes = response.body().readAllBytes();
            log.info("✅ PDF downloaded: {} bytes for PI: {}", pdfBytes.length, piNumbers);
            return pdfBytes;
        } else {
            String errorBody;
            try (var body = response.body()) {
                errorBody = new String(body.readAllBytes());
            }
            log.warn("⚠️ PDF download returned status {}: {}", response.statusCode(), errorBody);

            try {
                var errorJson = objectMapper.readTree(errorBody);
                if (errorJson.has("error") && errorJson.get("error").has("message")) {
                    var message = errorJson.get("error").get("message").asText();
                    log.warn("⚠️ PDF error message: {}", message);

                    if (message.contains("No data found") || message.contains("Cannot parse null")) {
                        log.warn("⚠️ PI {} may not have data yet. Skipping PDF download.", piNumbers);
                        return new byte[0];
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse error response: {}", e.getMessage());
            }

            // Don't throw - return empty so the caller (RetryUtil) can decide whether to retry.
            return new byte[0];
        }
    }
}
