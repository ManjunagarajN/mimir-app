package com.mimir.app.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.mimir.app.domain.QueryContext;
import com.mimir.app.domain.QueryVector;
import com.mimir.app.domain.RetrievedChunk;
import com.mimir.app.llm.LlmClient;
import com.mimir.app.mapper.ConfidenceBandMapper;
import com.mimir.app.rag.embedding.EmbeddingService;
import com.mimir.app.rag.prompt.PromptBuilder;
import com.mimir.app.rag.ranking.MmrRankingService;
import com.mimir.app.rag.retrieval.RetrievalService;
import com.mimir.app.response.PromptResult;
import com.mimir.app.util.ConfidenceLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryOrchestrator {

    private final EmbeddingService embeddingService;
    private final RetrievalService retrievalService;
    private final RerankerClient rerankerClient;
    private final MmrRankingService mmrRankingService;
    private final ConfidenceService confidenceService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;

    /**
     * Prepares a grounded prompt for the LLM using a standard RAG pipeline.
     * <p>
     * Flow:
     *
     * Embed → Retrieve → Diversify → Rerank → Add citations → Score confidence → Build prompt
     */
    public PromptResult preparePrompt(QueryContext context) {

        // Convert user query into vector representation for semantic similarity search
        QueryVector queryEmbedding = embeddingService.embed(context.getQuery());

        // Fetch top-N similar chunks (high recall; may include duplicates or weak matches)
        List<RetrievedChunk> candidates = retrievalService.findTopKByEmbeddingSimilarity(queryEmbedding, 30);

        // Apply MMR to reduce redundancy and improve diversity of selected chunks
        List<RetrievedChunk> diversified = mmrRankingService.select(queryEmbedding, candidates, 10);

        // Rerank using cross-encoder to get most relevant chunks (high precision)
        List<RetrievedChunk> ranked = rerankerClient.rerank(context.getQuery(), diversified, 5);

        // Assign sequential IDs for citation usage in prompt and UI ([1], [2], ...)
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).setId(i + 1);
        }
        List<RetrievedChunk> filtered =
                ranked.stream().filter(c -> c.getScore() >= 0.75f).toList();
        // Compute confidence score based on final ranked chunks
        double confidence = confidenceService.compute(ranked);

        // Convert raw score into user-friendly band (HIGH / MEDIUM / LOW)
        ConfidenceLevel level = ConfidenceBandMapper.toMap(confidence);

        // Build final prompt using selected chunks as context
        String prompt = promptBuilder.buildPrompt(context, ranked);

        // Return prompt along with metadata needed for streaming/UI
        return new PromptResult(prompt, confidence, level, ranked);
    }

    public void streamLLM(String prompt, Consumer<String> tokenConsumer, AtomicBoolean cancelled)
            throws IOException, InterruptedException {
        llmClient.stream(prompt, tokenConsumer, cancelled);
    }
}
