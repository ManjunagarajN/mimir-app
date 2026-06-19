package com.mimir.app.llm;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mimir.app.grpc.ChatServiceGrpc;
import com.mimir.app.grpc.ChatStreamResponse;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OllamaGRPCClient implements LlmClient {

    @Value("${spring.grpc.server.port:9090}")
    private int grpcPort;

    private ManagedChannel channel;
    private ChatServiceGrpc.ChatServiceStub stub;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .build();
        stub = ChatServiceGrpc.newStub(channel);
        log.info("gRPC self-call channel initialized on port {}", grpcPort);
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        if (channel != null) {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void stream(String prompt, Consumer<String> tokenConsumer, AtomicBoolean cancelled)
            throws IOException, InterruptedException {

        // Build gRPC request
        com.mimir.app.grpc.ChatStreamRequest grpcRequest = com.mimir.app.grpc.ChatStreamRequest.newBuilder()
                .addMessages(com.mimir.app.grpc.Message.newBuilder()
                        .setRole("user")
                        .setContent(prompt)
                        .build())
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        stub.chatStream(grpcRequest, new StreamObserver<ChatStreamResponse>() {

            @Override
            public void onNext(ChatStreamResponse response) {
                if (cancelled.get()) return;
                if ("token".equals(response.getType())) {
                    tokenConsumer.accept(response.getContent());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("gRPC self-call error", t);
                errorOccurred.set(true);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        latch.await();

        if (errorOccurred.get()) {
            throw new RuntimeException("gRPC self-call failed — check ChatGrpcService logs");
        }
    }
}
