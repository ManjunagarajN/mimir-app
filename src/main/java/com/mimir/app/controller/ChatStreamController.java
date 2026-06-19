package com.mimir.app.controller;

import javax.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mimir.app.request.ChatStreamRequest;
import com.mimir.app.service.ChatStreamService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChatStreamController {

    private final ChatStreamService chatStreamService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody ChatStreamRequest request) {
        // 30s timeout + 10s client heartbeat interval
        SseEmitter emitter = new SseEmitter(300_000L);

        // Delegate entirely to service. Controller returns immediately.
        chatStreamService.startStream(request, emitter);
        return emitter;
    }

    @PostMapping("/stop/{chatId}")
    public ResponseEntity<Void> stop(@PathVariable String chatId) {

        chatStreamService.stop(chatId);

        return ResponseEntity.ok().build();
    }
}
