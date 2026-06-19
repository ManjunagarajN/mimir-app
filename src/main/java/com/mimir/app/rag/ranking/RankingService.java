package com.mimir.app.rag.ranking;

import java.util.List;

import com.mimir.app.domain.RetrievedChunk;

@FunctionalInterface
public interface RankingService {

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK);
}
