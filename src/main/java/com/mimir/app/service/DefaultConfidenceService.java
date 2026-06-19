package com.mimir.app.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mimir.app.domain.RetrievedChunk;

@Service
public class DefaultConfidenceService implements ConfidenceService {
    public double compute(List<RetrievedChunk> ranked) {
        if (ranked == null || ranked.isEmpty()) return 0.0D;
        int k = Math.min(3, ranked.size());
        double sum = 0.0D;
        for (int i = 0; i < k; i++) sum += ((RetrievedChunk) ranked.get(i)).getScore();
        double avg = sum / k;
        double drop = (((RetrievedChunk) ranked.get(0)).getScore() - ((RetrievedChunk) ranked.get(k - 1)).getScore());
        double penalty = drop * 0.3D;
        double confidence = avg - penalty;
        return Math.max(0.0D, Math.min(1.0D, confidence));
    }
}
