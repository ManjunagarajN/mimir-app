package com.mimir.app.chunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mimir.app.domain.Chunk;

import lombok.extern.slf4j.Slf4j;

/**
 * SlidingWindowChunking
 *
 * Splits a document into fixed-size windows (by word count) with a
 * configurable overlap between consecutive windows. This is the simplest
 * and most predictable chunking strategy — useful as a baseline or when
 * document structure is unknown/unreliable.
 *
 * Example: windowSize=100, overlap=20
 *   chunk 1: words[0..100)
 *   chunk 2: words[80..180)
 *   chunk 3: words[160..260)
 *   ...
 */
@Slf4j
public class SlidingWindowChunking implements ChunkStrategy {

    private final int windowSize;
    private final int overlap;

    /**
     * @param windowSize number of words per chunk (must be > 0)
     * @param overlap    number of words shared between consecutive chunks
     *                   (must be >= 0 and < windowSize)
     */
    public SlidingWindowChunking(int windowSize, int overlap) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0");
        }
        if (overlap < 0 || overlap >= windowSize) {
            throw new IllegalArgumentException("overlap must be >= 0 and < windowSize");
        }
        this.windowSize = windowSize;
        this.overlap = overlap;
    }

    @Override
    public List<Chunk> chunk(String document) {
        List<Chunk> chunks = new ArrayList<>();

        if (document == null || document.isBlank()) {
            log.warn("Document is null or blank, returning empty chunk list");
            return chunks;
        }

        String[] words = document.trim().split("\\s+");
        int totalWords = words.length;
        int step = windowSize - overlap;

        int chunkIndex = 0;
        for (int start = 0; start < totalWords; start += step) {
            int end = Math.min(start + windowSize, totalWords);

            String text = String.join(" ", java.util.Arrays.asList(words).subList(start, end));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("strategy", "sliding_window");
            metadata.put("chunkIndex", chunkIndex);
            metadata.put("startWord", start);
            metadata.put("endWord", end);
            metadata.put("wordCount", end - start);

            chunks.add(new Chunk(text, metadata, 0f));

            chunkIndex++;

            // Stop once we've reached the end of the document
            if (end == totalWords) {
                break;
            }
        }

        log.info(
                "SlidingWindowChunking produced {} chunks | windowSize={} overlap={} totalWords={}",
                chunks.size(),
                windowSize,
                overlap,
                totalWords);

        return chunks;
    }

    @Override
    public String getDescription() {
        return "Fixed-size windows with overlap (windowSize=" + windowSize + ", overlap=" + overlap + ")";
    }
}
