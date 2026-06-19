package com.mimir.app.rag.ranking;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.mimir.app.domain.QueryVector;
import com.mimir.app.domain.RetrievedChunk;

/**
 * MMR ensures diversity by removing redundant chunks,
 *
 **/
@Service
public class MmrRankingService {

    // Balance: 1.0 = only relevance, 0.0 = only diversity
    private static final float LAMBDA = 0.7f;

    /**
     * Select topK chunks using Max Marginal Relevance (MMR).
     * Requires each chunk to have its embedding populated.
     */
    public List<RetrievedChunk> select(QueryVector queryVector, List<RetrievedChunk> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        final float[] q = queryVector.getVector();
        List<RetrievedChunk> selected = new ArrayList<>(topK);
        List<RetrievedChunk> remaining = new ArrayList<>(candidates);

        while (!remaining.isEmpty() && selected.size() < topK) {

            RetrievedChunk best = null;
            float bestScore = Float.NEGATIVE_INFINITY;

            for (RetrievedChunk c : remaining) {
                float rel = cosine(q, c.getEmbedding()); // relevance to query

                float div = 0f; // max similarity to already selected
                for (RetrievedChunk s : selected) {
                    div = Math.max(div, cosine(c.getEmbedding(), s.getEmbedding()));
                }

                float mmr = LAMBDA * rel - (1 - LAMBDA) * div;

                if (mmr > bestScore) {
                    bestScore = mmr;
                    best = c;
                }
            }

            selected.add(best);
            remaining.remove(best);
        }

        return selected;
    }

    /**
     * Cosine similarity (safe for zero norms)
     */
    //    private float cosine(float[] a, float[] b) {
    //        float dot = 0f, na = 0f, nb = 0f;
    //
    //        for (int i = 0; i < a.length; i++) {
    //            dot += a[i] * b[i];
    //            na += a[i] * a[i];
    //            nb += b[i] * b[i];
    //        }
    //
    //        float denom = (float) (Math.sqrt(na) * Math.sqrt(nb));
    //        return denom == 0f ? 0f : dot / denom;
    //    }

    private float cosine(float[] a, float[] b) {

        // Null safety
        if (a == null || b == null) {
            return 0f;
        }

        // Dimension safety
        if (a.length != b.length) {
            return 0f;
        }

        float dot = 0f;
        float na = 0f;
        float nb = 0f;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }

        float denom = (float) (Math.sqrt(na) * Math.sqrt(nb));

        return denom == 0f ? 0f : dot / denom;
    }
}
