package com.mimir.app.rag.retrieval;

import java.util.List;

import com.mimir.app.domain.QueryVector;
import com.mimir.app.domain.RetrievedChunk;

@FunctionalInterface
public interface RetrievalService {

    List<RetrievedChunk> findTopKByEmbeddingSimilarity(QueryVector queryVector, int topK);
}
