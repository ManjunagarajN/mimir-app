package com.mimir.app.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class QueryVector {
    // Core embedding
    private float[] vector;

    // Optional but useful
    private String model; // which embedding model
    private int dimension; // 1536, 768, etc.
    private long createdAt; // for caching / tracing

    // Optional: original query (debugging / observability)
    private String sourceText;
}
