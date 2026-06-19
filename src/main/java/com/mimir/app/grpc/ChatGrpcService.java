package com.mimir.app.grpc;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.grpc.server.service.GrpcService;

import com.mimir.app.llm.OllamaStreamClient;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * gRPC server implementation of ChatService.
 * Receives streaming requests and delegates token streaming to OllamaStreamClient (HTTP).
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

    private final OllamaStreamClient ollamaStreamClient;

    @Override
    public void chatStream(ChatStreamRequest request, StreamObserver<ChatStreamResponse> responseObserver) {

        AtomicBoolean cancelled = new AtomicBoolean(false);

        // Detect gRPC client cancellation
        io.grpc.Context.current()
                .addListener(
                        context -> {
                            cancelled.set(true);
                            log.info("gRPC client cancelled stream | chatId={}", request.getChatId());
                        },
                        Runnable::run);

        Thread.ofVirtual().start(() -> {
            try {
                String model = request.getModel().isBlank() ? "llama3.2" : request.getModel();
                String prompt = buildPrompt(request);

                log.info("gRPC stream started | chatId={} model={}", request.getChatId(), model);

                // Stream tokens from Ollama → gRPC response
                ollamaStreamClient.stream(
                        model,
                        prompt,
                        token -> {
                            if (cancelled.get()) return;
                            responseObserver.onNext(ChatStreamResponse.newBuilder()
                                    .setType("token")
                                    .setContent(token)
                                    .build());
                        },
                        cancelled);

                if (!cancelled.get()) {
                    responseObserver.onNext(
                            ChatStreamResponse.newBuilder().setType("done").build());
                    responseObserver.onCompleted();
                }

                log.info("gRPC stream completed | chatId={}", request.getChatId());

            } catch (Exception e) {
                log.error("gRPC stream error | chatId={}", request.getChatId(), e);
                responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription(e.getMessage())
                        .withCause(e)
                        .asRuntimeException());
            }
        });
    }

    private String buildPrompt(ChatStreamRequest request) {
        StringBuilder sb = new StringBuilder();
        for (var msg : request.getMessagesList()) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString().trim();
    }
}
