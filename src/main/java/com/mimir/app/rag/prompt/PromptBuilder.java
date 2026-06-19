package com.mimir.app.rag.prompt;

import java.util.List;

import com.mimir.app.domain.QueryContext;
import com.mimir.app.domain.RetrievedChunk;

@FunctionalInterface
public interface PromptBuilder {
    /**
     * Builds final LLM prompt using user query + retrieved context.
     */
    String buildPrompt(QueryContext ctx, List<RetrievedChunk> chunks);
}
