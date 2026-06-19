package com.mimir.app.response;

import java.util.List;

import com.mimir.app.domain.RetrievedChunk;
import com.mimir.app.util.ConfidenceLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class PromptResult {
    private String prompt;
    private double confidence;
    private ConfidenceLevel level;
    private List<RetrievedChunk> chunks;
}
