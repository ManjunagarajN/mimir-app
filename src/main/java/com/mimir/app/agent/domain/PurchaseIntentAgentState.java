package com.mimir.app.agent.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Holds the full working state of a Purchase Intent workflow run.
 *
 * NOTE: this is a superset of the original state class - it adds the fields
 * needed to persist a *failed* run to memory and resume it later
 * (workflowKey, lastUpdated, verified). If your existing domain class has
 * additional fields not shown here, merge them in rather than replacing the
 * file wholesale.
 */
@Data
@Builder
public class PurchaseIntentAgentState {

    private String query;
    private int iteration;
    private int maxIterations;

    @Builder.Default
    private List<String> actionHistory = new ArrayList<>();

    /** Name of the step currently being executed / the step that last failed. */
    private String currentStep;

    @Builder.Default
    private Map<String, Object> results = new HashMap<>();

    private String analysisResult;
    private SearchCriteria searchCriteria;
    private String accessToken;
    private Map<String, Object> searchResult;
    private MaxPurchaseIntentRequest intentRequest;
    private MaxPurchaseIntent mappedEntity;
    private Map<String, Object> createResult;
    private List<Long> createdPiNumbers;
    private List<Long> piNumbersUsed;
    private String pdfInfo;
    private String finalAnswer;
    private String error;

    // ================= RESUME / MEMORY SUPPORT =================

    /** Key this state is stored under in the workflow memory cache (e.g. user email). */
    private String workflowKey;

    /** Last time this state was written to memory. */
    private LocalDateTime lastUpdated;

    /** Whether the "verify created PI" step has already run successfully. */
    private boolean verified;

    public void addAction(String action) {
        if (actionHistory == null) actionHistory = new ArrayList<>();
        actionHistory.add(action);
    }

    public void incrementIteration() {
        this.iteration++;
    }

    /** True if the last attempt at {@link #currentStep} ended in an error. */
    public boolean isError() {
        return error != null && !error.isBlank();
    }

    /** True if the workflow finished all the way through without a live error. */
    public boolean isComplete() {
        return !isError() && finalAnswer != null && !finalAnswer.isBlank();
    }

    /** Clears the error flag so the failed step can be re-attempted on resume. */
    public void clearErrorForRetry() {
        this.error = null;
    }
}
