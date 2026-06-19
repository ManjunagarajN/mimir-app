package com.mimir.app.service;

import java.util.List;

import com.mimir.app.domain.RetrievedChunk;

public interface ConfidenceService {
    double compute(List<RetrievedChunk> ranked);
}
