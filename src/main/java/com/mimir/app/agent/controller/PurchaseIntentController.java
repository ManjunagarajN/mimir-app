package com.mimir.app.agent.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mimir.app.agent.domain.ApiResponse;
import com.mimir.app.agent.domain.PurchaseAgentRequest;
import com.mimir.app.agent.service.PurchaseIntentAgentService;
import com.mimir.app.agent.utils.AuthClient;
import com.mimir.app.agent.utils.PdfClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PurchaseIntentController {

    private final PurchaseIntentAgentService agentService;
    private final AuthClient authClient;
    private final PdfClient pdfClient;

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email and password required"));
        }

        try {
            String accessToken = authClient.login(email, password);
            if (accessToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid credentials"));
            }

            Map<String, Object> response = Map.of(
                    "accessToken", accessToken,
                    "email", email);

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("❌ Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/workflow/query")
    public ResponseEntity<ApiResponse<Map<String, Object>>> executeWithQuery(
            @RequestBody PurchaseAgentRequest request) {
        log.info("📝 Query-based workflow: {}", request.getQuery());

        try {
            var response = agentService.processFullWorkflow(request, null, new AtomicBoolean(false));
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping(value = "/workflow/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflowStream(@RequestBody PurchaseAgentRequest request) {
        var emitter = new SseEmitter(300000L);
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
                        var result = agentService.processFullWorkflow(
                                request,
                                token -> {
                                    try {
                                        if (!cancelled.get()) {
                                            emitter.send(SseEmitter.event()
                                                    .data(token)
                                                    .name("message"));
                                        }
                                    } catch (IOException e) {
                                        cancelled.set(true);
                                    }
                                },
                                cancelled);

                        if (!cancelled.get()) {
                            emitter.send(SseEmitter.event().data(result).name("result"));
                            emitter.send(SseEmitter.event().data("[DONE]").name("complete"));
                        }
                        emitter.complete();
                    } catch (Exception e) {
                        if (!cancelled.get()) emitter.completeWithError(e);
                    }
                })
                .start();

        return emitter;
    }

    @PostMapping("/workflow/pdf")
    public ResponseEntity<byte[]> executeWorkflowWithPdf(@RequestBody PurchaseAgentRequest request) {
        try {
            var response = agentService.processFullWorkflow(request, null, new AtomicBoolean(false));

            if (Boolean.TRUE.equals(response.get("success"))) {
                return buildFileResponse(response);
            } else {
                throw new RuntimeException("Workflow failed: " + response.get("error"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/workflow/pdf/redownload")
    public ResponseEntity<byte[]> redownloadPdf(@RequestBody PdfRedownloadRequest request) throws Exception {
        if (request.getPiNumbers() == null || request.getPiNumbers().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing PI numbers".getBytes(StandardCharsets.UTF_8));
        }

        String accessToken = request.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            accessToken = authClient.login(request.getEmail(), request.getPassword());
        }

        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login failed".getBytes(StandardCharsets.UTF_8));
        }

        byte[] fileBytes = pdfClient.downloadPurchaseIntentPdf(accessToken, request.getPiNumbers());
        if (fileBytes == null || fileBytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found or empty".getBytes(StandardCharsets.UTF_8));
        }

        boolean isZip = fileBytes.length >= 4
                && fileBytes[0] == 0x50
                && fileBytes[1] == 0x4B
                && fileBytes[2] == 0x03
                && fileBytes[3] == 0x04;
        boolean isPdf = fileBytes.length >= 5 && new String(fileBytes, 0, 5, StandardCharsets.UTF_8).equals("%PDF-");

        String extension;
        MediaType contentType;

        if (isZip) {
            extension = ".zip";
            contentType = MediaType.parseMediaType("application/zip");
        } else if (isPdf) {
            extension = ".pdf";
            contentType = MediaType.APPLICATION_PDF;
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Backend returned an unknown file format.".getBytes(StandardCharsets.UTF_8));
        }

        String filename = "PI-" + joinPiNumbers(request.getPiNumbers()) + extension;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Filename", filename)
                .header("Access-Control-Expose-Headers", "Content-Disposition, X-Filename")
                .contentType(contentType)
                .body(fileBytes);
    }

    private ResponseEntity<byte[]> buildFileResponse(Map<String, Object> response) {
        var fileBytes = Base64.getDecoder().decode((String) response.get("pdfContent"));

        boolean isZip = fileBytes.length >= 4
                && fileBytes[0] == 0x50
                && fileBytes[1] == 0x4B
                && fileBytes[2] == 0x03
                && fileBytes[3] == 0x04;
        boolean isPdf = fileBytes.length >= 5 && new String(fileBytes, 0, 5, StandardCharsets.UTF_8).equals("%PDF-");

        String extension = isZip ? ".zip" : (isPdf ? ".pdf" : ".bin");
        MediaType contentType = isZip
                ? MediaType.parseMediaType("application/zip")
                : (isPdf ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM);

        String filename = "purchase-intent-report" + extension;
        if (response.containsKey("createdPiNumbers")) {
            @SuppressWarnings("unchecked")
            var piNumbers = (List<Long>) response.get("createdPiNumbers");
            if (!piNumbers.isEmpty()) {
                filename = "PI-" + joinPiNumbers(piNumbers) + extension;
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Filename", filename)
                .header("Access-Control-Expose-Headers", "Content-Disposition, X-Filename")
                .contentType(contentType)
                .body(fileBytes);
    }

    private String joinPiNumbers(List<Long> piNumbers) {
        return String.join("-", piNumbers.stream().map(String::valueOf).toArray(String[]::new));
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("🛒 Purchase Intent Agent with Ollama is running!");
    }

    public static class PdfRedownloadRequest {
        private String email;
        private String password;
        private String accessToken;
        private List<Long> piNumbers;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public List<Long> getPiNumbers() {
            return piNumbers;
        }

        public void setPiNumbers(List<Long> piNumbers) {
            this.piNumbers = piNumbers;
        }
    }
}
