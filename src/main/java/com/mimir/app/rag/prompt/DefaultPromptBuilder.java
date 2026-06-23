package com.mimir.app.rag.prompt;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.mimir.app.domain.QueryContext;
import com.mimir.app.domain.RetrievedChunk;

@Service
public class DefaultPromptBuilder implements PromptBuilder {

    // ─────────────────────────────────────────────────────────────────────────
    // Shared formatting rules injected into every prompt mode
    // ─────────────────────────────────────────────────────────────────────────

    private static final String FORMATTING_RULES = """
            Formatting rules (always follow these):
            - Use **bold** to highlight key terms, names, dates, numbers, and important values
            - Use bullet points (- ) when listing 3 or more distinct facts or items
            - Use > blockquote when directly quoting a phrase from the source context
            - Use ### heading when the answer has 2 or more clearly distinct sections
            - For a single short fact, respond in plain prose — no bullets or headings needed
            - Never use bold on entire sentences — only on the specific key term within it
            """;

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public String buildPrompt(QueryContext ctx, List<RetrievedChunk> chunks) {
        Mode mode = resolveMode(ctx);
        String context = buildContext(chunks);

        return switch (mode) {
            case CHAT -> buildChatPrompt(ctx, context);
            case DOC -> buildDocPrompt(ctx, context);
            case SQL -> buildSqlPrompt(ctx, context);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mode resolution
    // ─────────────────────────────────────────────────────────────────────────

    private Mode resolveMode(QueryContext ctx) {
        try {
            return Mode.valueOf(ctx.getChatMode().toUpperCase());
        } catch (Exception e) {
            return Mode.CHAT; // safe default
        }
    }

    private enum Mode {
        CHAT,
        DOC,
        SQL
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prompt builders
    // ─────────────────────────────────────────────────────────────────────────

    private String buildChatPrompt(QueryContext ctx, String context) {
        return """
                You are a helpful assistant.

                Use the context if relevant, but you may answer generally.
                Keep the response natural and conversational.

                %s

                Context:
                %s

                Question:
                %s

                Answer:
                """.formatted(FORMATTING_RULES, context, ctx.getQuery());
    }

    private String buildDocPrompt(QueryContext ctx, String context) {
        return """
                You are a document analysis assistant.

                Answer the question using ONLY the context below.
                Cite sources using [number] immediately after the relevant sentence.
                Do not make up answers. If the answer is not in the context, say "I don't know".

                %s

                Additional doc rules:
                - Always end your answer with a "**Sources:**" section listing the [number] references used
                - Use > blockquote for any phrase taken verbatim from the context

                Context:
                %s

                Question:
                %s

                Answer:
                """.formatted(FORMATTING_RULES, context, ctx.getQuery());
    }

    private String buildSqlPrompt(QueryContext ctx, String context) {
        return """
                You are a data analysis assistant.

                Answer using the structured data context below.

                %s

                Additional data rules:
                - Prefer tables (markdown) over bullet points when comparing multiple values
                - Highlight exact figures using **bold**
                - If data is missing or ambiguous, say "No data available" rather than guessing

                Focus on:
                - Exact values
                - Aggregations if needed
                - Clear, concise output

                Data Context:
                %s

                Question:
                %s

                Answer:
                """.formatted(FORMATTING_RULES, context, ctx.getQuery());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Context builder
    // ─────────────────────────────────────────────────────────────────────────

    private String buildContext(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "No relevant context found.";
        }

        return IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    RetrievedChunk chunk = chunks.get(i);
                    String source = extractSource(chunk);
                    return "[%d] (source: %s)\n%s".formatted(i + 1, source, chunk.getContent());
                })
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Safely extracts parentSource from metadata map.
     * Falls back to "source" key, then "unknown" if neither exists.
     */
    private String extractSource(RetrievedChunk chunk) {
        if (chunk.getMetadata() == null) return "unknown";

        Object parentSource = chunk.getMetadata().get("parentSource");
        if (parentSource != null) return parentSource.toString();

        Object source = chunk.getMetadata().get("source");
        if (source != null) return source.toString();

        return "unknown";
    }
}
