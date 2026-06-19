package com.mimir.app.rag.embedding;

import com.mimir.app.domain.QueryVector;

public interface EmbeddingService {
    QueryVector embed(String text);
}
