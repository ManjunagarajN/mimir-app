package com.mimir.app.agent.controller;

import com.mimir.app.agent.client.AuthClient;
import com.mimir.app.agent.client.PdfClient;
import com.mimir.app.agent.domain.ApiResponse;
import com.mimir.app.agent.domain.PurchaseAgentRequest;
import com.mimir.app.agent.service.PurchaseIntentAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PurchaseIntentController {

    private final PurchaseIntentAgentService agentService;
    private final AuthClient authClient;
    private final PdfClient pdfClient;

    // ==================================================================
    // 1. Authentication Endpoint (Kept separate for UI token storage)
    // ==================================================================

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");

        if (isBlank(email) || isBlank(password)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email and password required"));
        }

        try {
            String accessToken = authClient.login(email, password);
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid credentials"));
            }

            Map<String, Object> response = Map.of("accessToken", accessToken, "email", email);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("❌ Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Login failed: " + e.getMessage()));
        }
    }

    // ==================================================================
    // 2. Unified Workflow Endpoint (Handles Create, Redownload, Auto-Login)
    // ==================================================================

    @PostMapping(value = "/workflow/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeUnifiedWorkflow(@RequestBody PurchaseAgentRequest request) {
        var emitter = new SseEmitter(300000L); // 5 minutes timeout
        var cancelled = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });

        emitter.onError((throwable) -> {
            cancelled.set(true);
            emitter.completeWithError(throwable);
        });

        new Thread(() -> {
            try {
                // Step 1: Auto-login if access token is missing
                if (isBlank(request.getAccessToken())) {
                    sendProgress(emitter, "🔐 Auto-logging in...\n");
                    String token = authClient.login(request.getEmail(), request.getPassword());

                    if (token == null) {
                        sendErrorAndComplete(emitter, "Login failed. Invalid credentials.");
                        return;
                    }

                    request.setAccessToken(token);
                    sendProgress(emitter, "✅ Login successful!\n\n");
                }

                // Step 2: Route to appropriate handler based on payload
                if (isNotEmpty(request.getPiNumbers()) && isBlank(request.getQuery())) {
                    // Scenario A: Redownload existing PDFs
                    handlePdfRedownload(request, emitter, cancelled);
                } else if (isNotBlank(request.getQuery())) {
                    // Scenario B: Full workflow (Search -> Create -> Download -> Ingest)
                    handleFullWorkflow(request, emitter, cancelled);
                } else {
                    sendErrorAndComplete(emitter, "Invalid request: Provide either 'query' or 'piNumbers'.");
                }

            } catch (Exception e) {
                log.error("❌ Unified workflow error: {}", e.getMessage(), e);
                if (!cancelled.get()) {
                    sendErrorAndComplete(emitter, "Error: " + e.getMessage());
                }
            }
        }).start();

        return emitter;
    }


    // ==================================================================
    // Helper Methods
    // ==================================================================

    private void handleFullWorkflow(PurchaseAgentRequest request, SseEmitter emitter, AtomicBoolean cancelled) throws IOException {
        var result = agentService.executePurchaseIntentWorkflow(
                request,
                token -> {
                    try {
                        if (!cancelled.get()) {
                            emitter.send(SseEmitter.event().data(token).name("message"));
                        }
                    } catch (IOException e) {
                        cancelled.set(true);
                    }
                },
                cancelled
        );

        if (!cancelled.get()) {
            emitter.send(SseEmitter.event().data(result).name("result"));
            emitter.send(SseEmitter.event().data("[DONE]").name("complete"));
        }
        emitter.complete();
    }

    private void handlePdfRedownload(PurchaseAgentRequest request, SseEmitter emitter, AtomicBoolean cancelled) throws Exception {
        sendProgress(emitter, "📄 Downloading PDF for PI(s): " + request.getPiNumbers() + "...\n");

        byte[] fileBytes = pdfClient.downloadPurchaseIntentPdf(request.getAccessToken(), request.getPiNumbers());

        if (fileBytes == null || fileBytes.length == 0) {
            sendErrorAndComplete(emitter, "File not found or empty.");
            return;
        }

        // Encode to Base64 and determine file type
        String base64Content = Base64.getEncoder().encodeToString(fileBytes);
        boolean isZip = fileBytes.length >= 4 && fileBytes[0] == 0x50 && fileBytes[1] == 0x4B
                && fileBytes[2] == 0x03 && fileBytes[3] == 0x04;

        Map<String, Object> result = Map.of(
                "success", true,
                "message", "PDF downloaded successfully",
                "pdfContent", base64Content,
                "pdfSize", fileBytes.length,
                "fileType", isZip ? "zip" : "pdf",
                "createdPiNumbers", request.getPiNumbers()
        );

        if (!cancelled.get()) {
            sendProgress(emitter, "✅ Download complete!\n");
            emitter.send(SseEmitter.event().data(result).name("result"));
            emitter.send(SseEmitter.event().data("[DONE]").name("complete"));
        }
        emitter.complete();
    }

    private void sendProgress(SseEmitter emitter, String message) throws IOException {
        emitter.send(SseEmitter.event().data(message).name("message"));
    }

    private void sendErrorAndComplete(SseEmitter emitter, String errorMessage) {
        try {
            Map<String, Object> errorResult = Map.of("success", false, "error", errorMessage);
            emitter.send(SseEmitter.event().data("❌ " + errorMessage + "\n").name("message"));
            emitter.send(SseEmitter.event().data(errorResult).name("result"));
            emitter.send(SseEmitter.event().data("[DONE]").name("complete"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    private boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    private boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}