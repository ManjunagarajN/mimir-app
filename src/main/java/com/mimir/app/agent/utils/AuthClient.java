package com.mimir.app.agent.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.agent.config.AgentConfig;
import com.mimir.app.agent.domain.LoginRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentConfig config;
    private final HttpLogUtil httpLogUtil;

    public String login(String email, String password) throws Exception {
        try {
            var loginRequest = new LoginRequest(email, password);
            var jsonBody = objectMapper.writeValueAsString(loginRequest);
            var url = config.getBaseUrl() + config.getApiVersion() + "/auth/login";

            log.info("🔐 Login URL: {}", url);
            httpLogUtil.logRequest("POST", url, "Content-Type: application/json", jsonBody);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
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
                String token = null;

                if (root.has("data") && root.get("data").has("accessToken")) {
                    token = root.get("data").get("accessToken").asText();
                } else if (root.has("accessToken")) {
                    token = root.get("accessToken").asText();
                }

                if (token != null) {
                    log.info("✅ Login successful! Token: {}", httpLogUtil.maskToken(token));
                    return token;
                }

                log.error("❌ Login failed: No access token in response");
                return null;
            } else {
                log.error("❌ Login failed: {} - {}", response.statusCode(), responseBody);
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Login error: {}", e.getMessage());
            throw e;
        }
    }
}
