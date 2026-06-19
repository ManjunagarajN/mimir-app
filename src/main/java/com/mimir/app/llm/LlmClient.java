package com.mimir.app.llm;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public interface LlmClient {
    /**
     * Stream tokens from LLM to callback.
     * @param prompt The full RAG prompt
     * @param tokenConsumer Callback for each token chunk
     * @param cancelled Cooperative cancellation flag (checked before each send)
     * @throws IOException on network/LLM errors
     * @throws InterruptedException if thread is interrupted
     */
    void stream(String prompt, Consumer<String> tokenConsumer, AtomicBoolean cancelled)
            throws IOException, InterruptedException;
}
