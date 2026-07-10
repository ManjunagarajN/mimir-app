package com.mimir.app.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mimir.app.chunker.ChunkStrategy;
import com.mimir.app.chunker.SlidingWindowChunking;
import com.mimir.app.domain.Chunk;
import com.mimir.app.domain.QueryVector;
import com.mimir.app.rag.embedding.EmbeddingService;
import com.mimir.app.rag.ingestion.DataInjectionRepository;
import com.mimir.app.util.*;
import com.mimir.app.util.parser.DocumentParser;
import com.mimir.app.util.parser.DocumentParserFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataInjectionService {
    private static final Logger log = LoggerFactory.getLogger(DataInjectionService.class);

    private final DataInjectionRepository dataInjectionRepository;
    private final EmbeddingService embeddingService;
    private final DocumentParserFactory documentParserFactory;

    private static final List<String> SUPPORTED_FILE_EXTS =
            List.of("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "html", "xml", "json", "csv", "md");

    private static final long MAX_FILE_SIZE_BYTES = 52_428_800L; // 50 MB
    private static final int MIN_CONTENT_LENGTH = 50;

    private static final ChunkStrategy CHUNK_STRATEGY = new SlidingWindowChunking(60, 6);

    private static final int MAX_TOKENS_PER_CHUNK = 512;
    private static final int MIN_TOKENS_PER_CHUNK = 20;
    private static final double TOKENS_PER_WORD = 4.0 / 3.0;

    public void ingest(List<MultipartFile> files, String inputSourceData) {
        if (files != null && !files.isEmpty()) {
            ingestFiles(files);
        } else if (inputSourceData != null && !inputSourceData.isBlank()) {
            ingestRawText(inputSourceData);
        } else {
            log.error("No data source provided");
        }
    }

    private void ingestFiles(List<MultipartFile> files) {
        log.info("Processing {} file(s)", files.size());

        int savedFiles = 0;

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            try {
                if (!isValidFile(file)) {
                    log.warn("Skipping invalid file: {}", filename);
                    continue;
                }

                String ext = extOf(filename);

                Optional<DocumentParser> parserOpt = documentParserFactory.getParser(ext);

                if (parserOpt.isEmpty()) {
                    log.warn("No parser found for extension: {} | file={}", ext, filename);
                    continue;
                }

                DocumentParser parser = parserOpt.get();

                // Read bytes once — parsers can open multiple streams from it
                byte[] fileBytes = file.getBytes();

                List<Map<String, Object>> records = parser.parse(fileBytes, filename);

                if (records == null || records.isEmpty()) {
                    log.warn("Parser returned no records for: {}", filename);
                    continue;
                }

                String content = parser.generateRetrievalText(records);

                if (content == null || content.isBlank()) {
                    log.warn("Empty retrieval text for: {}", filename);
                    continue;
                }

                content = cleanContent(content);
                int originalLength = content.length();
                content = deduplicateRepeatedParagraphs(content);

                if (content.length() != originalLength) {
                    log.warn(
                            "Deduplicated content for {} | {} -> {} chars", filename, originalLength, content.length());
                }

                if (content.length() < MIN_CONTENT_LENGTH) {
                    log.warn("Content too short: {} ({} chars)", filename, content.length());
                    continue;
                }

                log.info(
                        "Document loaded | file={} sizeBytes={} chars={} words={} estimatedTokens={}",
                        filename,
                        file.getSize(),
                        content.length(),
                        content.trim().split("\\s+").length,
                        estimateTokens(content));

                int savedChunks = chunkEmbedAndSavePerChunk(content, filename, "document", ext);

                if (savedChunks > 0) {
                    savedFiles++;
                    log.info("Saved {} chunks for file: {}", savedChunks, filename);
                }

            } catch (Exception e) {
                log.warn("Could not extract from: {} ({})", filename, e.getMessage());
            }
        }

        log.info("File ingestion complete | total={} savedFiles={}", files.size(), savedFiles);
    }

    private void ingestRawText(String inputSourceData) {
        log.info(
                "Processing inline source data | chars={} estimatedTokens={}",
                inputSourceData.length(),
                estimateTokens(inputSourceData));

        if (inputSourceData.length() < MIN_CONTENT_LENGTH) {
            log.warn("inputSourceData too short ({} chars)", inputSourceData.length());
            return;
        }

        int savedChunks = chunkEmbedAndSavePerChunk(inputSourceData, "inline-source", "raw_text", "text");
        log.info("Saved {} chunks for inline source data", savedChunks);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Core: chunk → token-check → embed → save
    // ─────────────────────────────────────────────────────────────────────

    private int chunkEmbedAndSavePerChunk(String content, String source, String sourceType, String entityType) {

        List<Chunk> chunks = CHUNK_STRATEGY.chunk(content);

        if (chunks.isEmpty()) {
            log.warn("No chunks produced for source={}", source);
            return 0;
        }

        log.info(
                "Chunking complete | source={} strategy={} chunks={}",
                source,
                CHUNK_STRATEGY.getDescription(),
                chunks.size());

        int totalChunks = chunks.size();
        int savedCount = 0;
        int skippedTokenLimit = 0;
        int skippedTooShort = 0;
        int totalTokens = 0;
        int rawDocTokens = estimateTokens(content);

        for (int i = 0; i < totalChunks; i++) {
            Chunk chunk = chunks.get(i);
            String chunkSource = source + "#chunk-" + i;
            int chunkTokens = estimateTokens(chunk.getText());
            totalTokens += chunkTokens;

            if (chunkTokens < MIN_TOKENS_PER_CHUNK) {
                log.info(
                        "Skipping short chunk {}/{} | source={} estimatedTokens={} (min={})",
                        i + 1,
                        totalChunks,
                        source,
                        chunkTokens,
                        MIN_TOKENS_PER_CHUNK);
                skippedTooShort++;
                continue;
            }

            if (chunkTokens > MAX_TOKENS_PER_CHUNK) {
                log.warn(
                        "Skipping oversized chunk {}/{} | source={} estimatedTokens={} (max={})",
                        i + 1,
                        totalChunks,
                        source,
                        chunkTokens,
                        MAX_TOKENS_PER_CHUNK);
                skippedTokenLimit++;
                continue;
            }

            log.info(
                    "Processing chunk {}/{} | source={} words={} estimatedTokens={}",
                    i + 1,
                    totalChunks,
                    source,
                    chunk.getText().split("\\s+").length,
                    chunkTokens);

            try {
                QueryVector vector = embeddingService.embed(chunk.getText());

                QueryVector chunkVector = QueryVector.builder()
                        .vector(vector.getVector())
                        .model("nomic-embed-text")
                        .dimension(vector.getVector().length)
                        .createdAt(System.currentTimeMillis())
                        .sourceText(chunk.getText())
                        .build();

                boolean saved = dataInjectionRepository.save(
                        chunk.getText(), chunkVector, source, chunkSource, sourceType, entityType, i, totalChunks);

                if (saved) {
                    savedCount++;
                } else {
                    log.warn("No rows affected for chunk {} of source={}", i, source);
                }

            } catch (Exception e) {
                log.warn("Failed to embed/save chunk {} for source={}: {}", i, source, e.getMessage());
            }
        }

        int efficiency = (int) ((rawDocTokens * 100.0) / Math.max(totalTokens, 1));
        log.info(
                "Token budget | source={} rawTokens={} storedTokens={} efficiency={}%",
                source, rawDocTokens, totalTokens, efficiency);

        log.info(
                "Chunk ingestion complete | source={} total={} saved={} "
                        + "skippedTooShort={} skippedTokenLimit={} totalEstimatedTokens={}",
                source,
                totalChunks,
                savedCount,
                skippedTooShort,
                skippedTokenLimit,
                totalTokens);

        return savedCount;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Text cleaning
    // ─────────────────────────────────────────────────────────────────────

    private String cleanContent(String content) {
        return content.replaceAll("\\n{3,}", "\n\n")
                .replaceAll("(?i)page\\s+\\d+\\s*(of\\s*\\d+)?", "")
                .replaceAll("-\\s*\\d+\\s*-", "")
                .replaceAll("[\\-_]{3,}", "")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }

    private String deduplicateRepeatedParagraphs(String content) {
        String[] paragraphs = content.split("\\r?\\n\\s*\\r?\\n+");
        StringBuilder result = new StringBuilder();
        String lastNormalized = null;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            String normalized = trimmed.replaceAll("\\s+", " ");
            if (normalized.equals(lastNormalized)) continue;

            result.append(trimmed).append("\n\n");
            lastNormalized = normalized;
        }

        return result.toString().trim();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Token estimation & helpers
    // ─────────────────────────────────────────────────────────────────────

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int wordCount = text.trim().split("\\s+").length;
        return (int) Math.ceil(wordCount * TOKENS_PER_WORD);
    }

    private String extOf(String filename) {
        if (filename == null) return "unknown";
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1).toLowerCase() : "unknown";
    }

    private boolean isValidFile(MultipartFile file) {
        if (file.isEmpty()) return false;

        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) return false;

        String ext = extOf(name);
        if (!SUPPORTED_FILE_EXTS.contains(ext)) {
            log.warn("Unsupported file extension: {}", ext);
            return false;
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            log.warn("File too large: {} ({} bytes)", name, file.getSize());
            return false;
        }

        return true;
    }
}
