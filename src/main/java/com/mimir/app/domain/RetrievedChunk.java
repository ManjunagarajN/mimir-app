package com.mimir.app.domain;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Candidate chunk returned from vector retrieval.
 * Used as input for ranking (MMR / reranker).
 */
@Data
@Builder
@AllArgsConstructor
public class RetrievedChunk {

    /**
     * citation used in prompt
     */
    private int id;

    /**
     * Text content used in prompt
     */
    private String content;

    /**
     * Similarity score (1 - cosine distance)
     */
    private float score;

    /**
     * Optional: required only for MMR
     */
    private float[] embedding;

    /**
     * Optional: source/context info
     */
    private Map<String, Object> metadata;
}
