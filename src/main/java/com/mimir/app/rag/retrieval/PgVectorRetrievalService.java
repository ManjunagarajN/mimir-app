package com.mimir.app.rag.retrieval;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.mimir.app.domain.QueryVector;
import com.mimir.app.domain.RetrievedChunk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PgVectorRetrievalService implements RetrievalService {

    private final JdbcTemplate jdbcTemplate;

    private static final float MIN_SCORE = 0.3f; // tune based on testing

    private static final String DOCUMENT_SQL = """
        SELECT
            value,
            data,
            metadata,
            source_type,
            entity_type,
            (1 - (data OPERATOR(app.<=>) CAST(? AS app.vector))) AS score
        FROM app.embeddings
        WHERE status = 'active'
          AND (1 - (data OPERATOR(app.<=>) CAST(? AS app.vector))) >= ?
        ORDER BY data OPERATOR(app.<=>) CAST(? AS app.vector)
        LIMIT ?
        """;

    private static final String CHAT_SQL = """
        SELECT
            value,
            data,
            metadata,
            null AS source_type,
            null AS entity_type,
            (1 - (data OPERATOR(app.<=>) CAST(? AS app.vector))) AS score
        FROM app.user_embeddings
        WHERE (1 - (data OPERATOR(app.<=>) CAST(? AS app.vector))) >= ?
        ORDER BY data OPERATOR(app.<=>) CAST(? AS app.vector)
        LIMIT ?
        """;

    @Override
    public List<RetrievedChunk> findTopKByEmbeddingSimilarity(QueryVector queryVector, int topK) {

        Object pgVec = toPgVector(queryVector.getVector());

        log.info("Retrieval started | topK={} vectorDim={}", topK, queryVector.getDimension());

        try {
            // ── 1. Query ingested documents ──────────────────────────────
            List<RetrievedChunk> documentChunks =
                    jdbcTemplate.query(DOCUMENT_SQL, this::mapRow, pgVec, pgVec, MIN_SCORE, pgVec, topK);

            log.info("Retrieval | documents={}", documentChunks.size());

            // ── 2. Query chat history (half of topK to avoid dominating) ─
            List<RetrievedChunk> chatChunks =
                    jdbcTemplate.query(CHAT_SQL, this::mapRow, pgVec, pgVec, MIN_SCORE, pgVec, Math.max(1, topK / 2));

            log.info("Retrieval | chatHistory={}", chatChunks.size());

            // ── 3. Merge + sort by score descending ──────────────────────
            List<RetrievedChunk> merged = new ArrayList<>();
            merged.addAll(documentChunks);
            merged.addAll(chatChunks);
            merged.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

            List<RetrievedChunk> result = merged.stream().limit(topK).toList();

            log.info(
                    "Retrieval complete | documents={} chat={} merged={} returning={}",
                    documentChunks.size(),
                    chatChunks.size(),
                    merged.size(),
                    result.size());

            return result;

        } catch (Exception ex) {
            log.error("Retrieval failed", ex);
            return List.of();
        }
    }

    // ── row mapper ───────────────────────────────────────────────────────

    private RetrievedChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
        float[] embedding = null;
        Object vectorObj = rs.getObject("data");
        if (vectorObj instanceof com.pgvector.PGvector pgVector) {
            embedding = pgVector.toArray();
        }

        return RetrievedChunk.builder()
                .content(rs.getString("value"))
                .score(rs.getFloat("score"))
                .embedding(embedding)
                .build();
    }

    private Object toPgVector(float[] embedding) {
        return new com.pgvector.PGvector(embedding);
    }
}
