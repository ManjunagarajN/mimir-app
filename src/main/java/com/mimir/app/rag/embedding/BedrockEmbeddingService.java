package com.mimir.app.rag.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.mimir.app.domain.QueryVector;

@Service
@ConditionalOnProperty(name = "rag.embedding-provider", havingValue = "bedrock")
public class BedrockEmbeddingService implements EmbeddingService {
    // private final BedrockRuntimeClient bedrockClient;

    @Override
    public QueryVector embed(String text) {
        return null;
    }
}
