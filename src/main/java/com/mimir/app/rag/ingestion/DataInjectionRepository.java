package com.mimir.app.rag.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.domain.QueryVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInjectionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String INSERT_SQL = """
                INSERT INTO app.embeddings
                                                     (source_id, value, data, metadata, source_type, entity_type, content_hash, status)
                                                     VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, 'active')
                                                     ON CONFLICT (content_hash, version) DO NOTHING
            """;

    public boolean save(String content, QueryVector vector, String source, String sourceType, String entityType) {
        try {
            Map<String, Object> meta = Map.of(
                    "source", source,
                    "extractedChars", content.length());
            String metadataJson = objectMapper.writeValueAsString(meta);

            String hash = DigestUtils.sha256Hex(content);
            UUID sourceId = UUID.randomUUID();

            int rows = jdbcTemplate.update(
                    INSERT_SQL,
                    sourceId,
                    content,
                    new com.pgvector.PGvector(vector.getVector()),
                    metadataJson,
                    sourceType,
                    entityType,
                    hash);

            if (rows > 0) {
                log.info("Saved embedding | source={} sourceId={} chars={} hash={}", source, sourceId, content.length(), hash);
            } else {
                log.info("Skipped duplicate embedding | source={} hash={}", source, hash);
            }

            return rows > 0;

        } catch (Exception e) {
            log.error("Failed to save embedding for source={}: {}", source, e.getMessage(), e);
            return false;
        }
    }
}