package com.mimir.app.rag.prompt;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.mimir.app.domain.QueryContext;
import com.mimir.app.domain.RetrievedChunk;

@Service
public class DefaultPromptBuilder implements PromptBuilder {

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

    // -------------------- MODE --------------------

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

    // -------------------- PROMPTS --------------------
    private String buildChatPrompt(QueryContext ctx, String context) {
        return """
                You are a helpful assistant.

                Use the context if relevant, but you may answer generally.
                Keep the response natural and conversational.

                Context:
                %s

                Question:
                %s

                Answer:
                """.formatted(context, ctx.getQuery());
    }

    private String buildDocPrompt(QueryContext ctx, String context) {
        return """
                Answer the question using ONLY the context below.
                Cite sources using [number].
                Do not make up answers. If not found, say "I don't know".

                Context:
                %s

                Question:
                %s

                Answer:
                """.formatted(context, ctx.getQuery());
    }

    private String buildSqlPrompt(QueryContext ctx, String context) {
        return """
                Answer using structured data context.

                Focus on:
                - Exact values
                - Aggregations if needed
                - Clear, concise output

                If data is missing, say "No data available".

                Data Context:
                %s

                Question:
                %s

                Answer:
                """.formatted(context, ctx.getQuery());
    }

    // -------------------- CONTEXT --------------------

    private String buildContext(List<RetrievedChunk> chunks) {

        if (chunks == null || chunks.isEmpty()) {
            return "No relevant context found.";
        }

        return IntStream.range(0, chunks.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + chunks.get(i).getContent())
                .collect(Collectors.joining("\n\n"));
    }
}
