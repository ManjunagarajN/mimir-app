package com.mimir.app.rag.ingestion;

import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.domain.QueryVector;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataInjectionRepository {
    private static final Logger log = LoggerFactory.getLogger(DataInjectionRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String UPSERT_SQL = """
                INSERT INTO app.embeddings
                    (source_id, value, data, metadata, source_type, entity_type, content_hash, status)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, 'active')
                ON CONFLICT (content_hash, version)
                DO UPDATE SET
                    value      = EXCLUDED.value,
                    data       = EXCLUDED.data,
                    status     = 'active',
                    updated_at = now()
            """;

    /**
     * Save a single chunk with grouping metadata.
     *
     * @param parentSource  the original file/source name (e.g. "report.pdf")
     * @param chunkIndex    zero-based index of this chunk within the document
     * @param totalChunks   total number of chunks the document was split into
     */
    public boolean save(
            String content,
            QueryVector vector,
            String parentSource,
            String chunkSource, // e.g. "report.pdf#chunk-0"
            String sourceType,
            String entityType,
            int chunkIndex,
            int totalChunks) {
        try {
            Map<String, Object> meta = Map.of(
                    "source", chunkSource,
                    "parentSource", parentSource,
                    "chunkIndex", chunkIndex,
                    "totalChunks", totalChunks,
                    "extractedChars", content.length());
            String metadataJson = objectMapper.writeValueAsString(meta);

            String hash = DigestUtils.sha256Hex(content);
            UUID sourceId = UUID.randomUUID();

            int rows = jdbcTemplate.update(
                    UPSERT_SQL,
                    sourceId,
                    content,
                    new com.pgvector.PGvector(vector.getVector()),
                    metadataJson,
                    sourceType,
                    entityType,
                    hash);

            if (rows > 0) {
                log.trace(
                        "Upserted chunk | parent={} chunk={}/{} sourceId={} chars={} hash={}",
                        parentSource,
                        chunkIndex + 1,
                        totalChunks,
                        sourceId,
                        content.length(),
                        hash);
            } else {
                log.trace(
                        "No-op upsert (identical content) | parent={} chunk={}/{} hash={}",
                        parentSource,
                        chunkIndex + 1,
                        totalChunks,
                        hash);
            }

            return rows > 0;

        } catch (Exception e) {
            log.error(
                    "Failed to upsert chunk | parent={} chunk={}/{}: {}",
                    parentSource,
                    chunkIndex + 1,
                    totalChunks,
                    e.getMessage(),
                    e);
            return false;
        }
    }
}
