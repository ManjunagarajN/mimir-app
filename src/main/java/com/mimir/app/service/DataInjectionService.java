package com.mimir.app.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mimir.app.chunker.ChunkStrategy;
import com.mimir.app.chunker.SlidingWindowChunking;
import com.mimir.app.domain.Chunk;
import com.mimir.app.domain.QueryVector;
import com.mimir.app.rag.embedding.EmbeddingService;
import com.mimir.app.rag.ingestion.DataInjectionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataInjectionService {

    private final Tika tika;
    private final DataInjectionRepository dataInjectionRepository;
    private final EmbeddingService embeddingService;

    private static final List<String> SUPPORTED_FILE_EXTS =
            List.of("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "html", "xml", "json", "csv", "md");

    private static final long MAX_FILE_SIZE_BYTES = 52_428_800L; // 50 MB
    private static final int MIN_CONTENT_LENGTH = 50;

    private static final ChunkStrategy CHUNK_STRATEGY = new SlidingWindowChunking(100, 20);

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

                String content = tika.parseToString(file.getInputStream()).trim();
                log.info("content {} :", content);
                int originalLength = content.length();
                content = deduplicateRepeatedParagraphs(content);

                if (content.length() != originalLength) {
                    log.warn(
                            "Deduplicated content for {} | {} chars -> {} chars",
                            filename,
                            originalLength,
                            content.length());
                }

                if (content.length() < MIN_CONTENT_LENGTH) {
                    log.warn("Content too short: {} ({} chars)", filename, content.length());
                    continue;
                }

                boolean saved = chunkEmbedAndSaveAsSingleRecord(content, filename, "document", extOf(filename));

                if (saved) {
                    savedFiles++;
                }

            } catch (Exception e) {
                log.warn("Could not extract from: {} ({})", filename, e.getMessage());
            }
        }

        log.info("File ingestion complete | total={} savedFiles={}", files.size(), savedFiles);
    }

    private void ingestRawText(String inputSourceData) {
        log.info("Processing inline source data ({} chars)", inputSourceData.length());

        if (inputSourceData.length() < MIN_CONTENT_LENGTH) {
            log.warn("inputSourceData too short ({} chars)", inputSourceData.length());
            return;
        }

        chunkEmbedAndSaveAsSingleRecord(inputSourceData, "inline-source", "raw_text", "text");
    }

    /**
     * Removes consecutive duplicate paragraphs, which commonly occur when
     * Tika extracts text from PDFs containing repeated/layered content streams.
     */
    private String deduplicateRepeatedParagraphs(String content) {
        String[] paragraphs = content.split("\\r?\\n\\s*\\r?\\n+");
        StringBuilder result = new StringBuilder();
        String lastNormalized = null;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            String normalized = trimmed.replaceAll("\\s+", " ");

            if (normalized.equals(lastNormalized)) {
                continue; // skip exact consecutive duplicate
            }

            result.append(trimmed).append("\n\n");
            lastNormalized = normalized;
        }

        return result.toString().trim();
    }

    private boolean chunkEmbedAndSaveAsSingleRecord(
            String content, String source, String sourceType, String entityType) {
        List<Chunk> chunks = CHUNK_STRATEGY.chunk(content);

        if (chunks.isEmpty()) {
            log.warn("No chunks produced for source={}", source);
            return false;
        }

        log.info(
                "Chunking complete | source={} strategy={} chunks={}",
                source,
                CHUNK_STRATEGY.getDescription(),
                chunks.size());

        List<float[]> chunkVectors = new ArrayList<>();
        for (Chunk chunk : chunks) {
            try {
                QueryVector vector = embeddingService.embed(chunk.getText());
                chunkVectors.add(vector.getVector());
            } catch (Exception e) {
                log.warn("Failed to embed chunk for source={}: {}", source, e.getMessage());
            }
        }

        if (chunkVectors.isEmpty()) {
            log.warn("No chunk vectors produced for source={}", source);
            return false;
        }

        int dimension = chunkVectors.get(0).length;
        float[] combined = meanPool(chunkVectors, dimension);

        log.info(
                "Combined {} chunk vectors into single vector | source={} dimension={}",
                chunkVectors.size(),
                source,
                dimension);

        QueryVector combinedVector = QueryVector.builder()
                .vector(combined)
                .model("nomic-embed-text")
                .dimension(dimension)
                .createdAt(System.currentTimeMillis())
                .sourceText(content)
                .build();
        return dataInjectionRepository.save(content, combinedVector, source, sourceType, entityType);
    }

    private float[] meanPool(List<float[]> vectors, int dimension) {
        float[] sum = new float[dimension];

        for (float[] vec : vectors) {
            for (int i = 0; i < dimension; i++) {
                sum[i] += vec[i];
            }
        }

        float[] mean = new float[dimension];
        int count = vectors.size();
        for (int i = 0; i < dimension; i++) {
            mean[i] = sum[i] / count;
        }

        return mean;
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
