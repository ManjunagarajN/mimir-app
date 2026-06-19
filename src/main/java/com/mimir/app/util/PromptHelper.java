package com.mimir.app.util;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.mimir.app.domain.RetrievedChunk;

@Component
public class PromptHelper {
    public String formatContext(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return "No additional context provided.";
        StringBuilder sb = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            if (chunk.getId() > 0) sb.append("[").append(chunk.getId()).append("] ");
            String sourceName = extractSourceName(chunk.getMetadata());
            sb.append("Source: ").append(sourceName).append("\n");
            String metaLine = formatMetadata(chunk.getMetadata());
            if (!metaLine.isEmpty()) sb.append("Meta: ").append(metaLine).append("\n");
            sb.append("Relevance: ")
                    .append(String.format("%.2f", new Object[] {Float.valueOf(chunk.getScore())}))
                    .append("\n");
            sb.append("Content:\n").append(chunk.getContent()).append("\n");
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    private String extractSourceName(Map<String, Object> metadata) {
        if (metadata == null) return "Unknown";
        for (String key : new String[] {"source", "source_name", "title", "document_name", "name"}) {
            Object value = metadata.get(key);
            if (value != null) return value.toString();
        }
        return "Unknown";
    }

    private String formatMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return "";
        Set<String> skipKeys = Set.of("source", "source_name", "title", "document_name", "name");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (skipKeys.contains(entry.getKey())) continue;
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(key).append(": ").append(value);
            }
        }
        return sb.toString();
    }

    public String formatHistory(List<String> history) {
        if (history == null || history.isEmpty()) return "No previous conversation.";
        StringBuilder sb = new StringBuilder();
        //    for (ChatTurn turn : history)
        //      sb.append(turn.role()).append(": ").append(turn.message()).append("\n");
        return sb.toString();
    }
}
