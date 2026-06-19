package com.mimir.app.mapper;

import org.springframework.stereotype.Service;

import com.mimir.app.util.ConfidenceLevel;

/**
 * Discrete confidence bands for RAG retrieval quality.
 * Emitted in the SSE response to signal grounding reliability to the UI.
 */

/**
 * Maps a raw numeric confidence score → discrete band.
 * Stateless, thread-safe, zero dependencies, inline constants only.
 */
@Service
public class ConfidenceBandMapper {

    // 🔹 Thresholds as inline constants (easy to tune, no property files)
    private static final double HIGH_THRESHOLD = 0.75;
    private static final double MEDIUM_THRESHOLD = 0.45;
    private static final double MIN_VALID_SCORE = 0.0;
    private static final double MAX_VALID_SCORE = 1.0;

    /**
     * Converts raw confidence score to a stable band.
     * Clamps input to [0.0, 1.0] to prevent drift/NaN from breaking UI logic.
     *
     * @param confidence Raw score from reranker/scoring service
     * @return ConfidenceLevel.HIGH | MEDIUM | LOW
     */
    public static ConfidenceLevel toMap(double confidence) {
        // Clamp to valid range
        double clamped = Math.max(MIN_VALID_SCORE, Math.min(MAX_VALID_SCORE, confidence));

        if (clamped >= HIGH_THRESHOLD) {
            return ConfidenceLevel.HIGH;
        } else if (clamped >= MEDIUM_THRESHOLD) {
            return ConfidenceLevel.MEDIUM;
        } else {
            return ConfidenceLevel.LOW;
        }
    }
}
