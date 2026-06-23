package com.mimir.app.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mimir.app.domain.QueryContext;
import com.mimir.app.mapper.RequestMapper;
import com.mimir.app.request.ChatStreamRequest;
import com.mimir.app.response.PromptResult;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ChatStreamService {
    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);
    private final QueryOrchestrator orchestrator;
    private final Semaphore llmConcurrencyLimiter;
    private final RequestMapper requestMapper;

    private final Map<String, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private static final String NO_CONTEXT_MESSAGE = "I don't have information about that in the available documents.";

    public void startStream(ChatStreamRequest request, SseEmitter emitter) {

        final String chatId = String.valueOf(request.getChatId());

        AtomicBoolean cancelled = new AtomicBoolean(false);

        cancellations.put(chatId, cancelled);
        emitters.put(chatId, emitter);

        emitter.onCompletion(() -> {
            cleanup(chatId);
            log.info("SSE completed | chatId={}", chatId);
        });

        emitter.onTimeout(() -> {
            cancelled.set(true);
            cleanup(chatId);
            log.warn("SSE timeout | chatId={}", chatId);
            emitter.complete();
        });

        emitter.onError(ex -> {
            cancelled.set(true);
            cleanup(chatId);
            log.error("SSE error | chatId={}", chatId, ex);
        });

        Thread.ofVirtual().start(() -> {
            boolean permitAcquired = false;

            try {

                llmConcurrencyLimiter.acquire();
                permitAcquired = true;

                log.info("Stream started | chatId={}", chatId);

                if (cancelled.get()) {
                    log.info("Cancelled before processing | chatId={}", chatId);
                    return;
                }

                QueryContext context = requestMapper.toMap(request);

                PromptResult prompt = orchestrator.preparePrompt(context);

                if (cancelled.get()) {
                    log.info("Cancelled after prompt preparation | chatId={}", chatId);
                    emitter.complete();
                    return;
                }

                emitter.send(SseEmitter.event()
                        .name("confidence")
                        .data(Map.of(
                                "score", prompt.getConfidence(),
                                "level", prompt.getLevel().name())));

                if (prompt.getChunks() == null || prompt.getChunks().isEmpty()) {

                    emitter.send(SseEmitter.event().name("token").data(NO_CONTEXT_MESSAGE));

                    emitter.send(SseEmitter.event().name("done").data("completed"));

                    emitter.complete();
                    return;
                }

                orchestrator.streamLLM(
                        prompt.getPrompt(),
                        token -> {
                            if (cancelled.get()) {
                                return;
                            }

                            try {

                                emitter.send(SseEmitter.event().name("token").data(token));

                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        cancelled);

                if (cancelled.get()) {
                    log.info("Cancelled during streaming | chatId={}", chatId);
                    emitter.complete();
                    return;
                }

                emitter.send(SseEmitter.event().name("done").data("completed"));

                emitter.complete();

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                cancelled.set(true);

                log.info("Stream interrupted | chatId={}", chatId);

                emitter.complete();

            } catch (Exception e) {

                log.error("Stream error | chatId={}", chatId, e);

                emitter.completeWithError(e);

            } finally {

                cleanup(chatId);

                if (permitAcquired) {
                    llmConcurrencyLimiter.release();
                }
            }
        });
    }

    public void stop(String chatId) {

        log.info("Stop requested | chatId={}", chatId);

        AtomicBoolean cancelled = cancellations.get(chatId);

        if (cancelled != null) {
            cancelled.set(true);
        }

        SseEmitter emitter = emitters.get(chatId);

        if (emitter != null) {
            emitter.complete();
        }

        cleanup(chatId);
    }

    private void cleanup(String chatId) {

        cancellations.remove(chatId);
        emitters.remove(chatId);
    }
}
