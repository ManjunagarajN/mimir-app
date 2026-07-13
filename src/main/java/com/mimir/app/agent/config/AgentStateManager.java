package com.mimir.app.agent.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.mimir.app.agent.domain.PurchaseIntentAgentState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentStateManager {

    private final Cache<String, PurchaseIntentAgentState> workflowStateCache;

    public PurchaseIntentAgentState createInitialState(String query) {
        return PurchaseIntentAgentState.builder()
                .query(query != null ? query : "Purchase Intent Workflow")
                .iteration(0)
                .maxIterations(10)
                .actionHistory(new ArrayList<>())
                .currentStep("plan")
                .results(new HashMap<>())
                .build();
    }

    public void addAction(PurchaseIntentAgentState state, String action) {
        state.addAction(action);
        log.debug("Action added: {}", action);
    }

    public void incrementIteration(PurchaseIntentAgentState state) {
        state.incrementIteration();
    }

    public void updateStep(PurchaseIntentAgentState state, String step) {
        state.setCurrentStep(step);
        log.debug("Step updated: {}", step);
    }

    public void setError(PurchaseIntentAgentState state, String error) {
        state.setError(error);
        state.setFinalAnswer("Workflow failed at step '" + state.getCurrentStep() + "': " + error);
    }

    public boolean isComplete(PurchaseIntentAgentState state) {
        return state.isComplete();
    }

    public boolean isError(PurchaseIntentAgentState state) {
        return state.isError();
    }

    public void storeResult(PurchaseIntentAgentState state, String key, Object value) {
        state.getResults().put(key, value);
    }

    public Map<String, Object> getResults(PurchaseIntentAgentState state) {
        return state.getResults();
    }

    // ==================================================================
    // RESUME / MEMORY SUPPORT
    //
    // A workflow that throws mid-way has already accumulated useful state
    // (access token, search criteria, mapped entity, created PI numbers...).
    // Instead of discarding that on failure, we persist it here keyed by a
    // "workflow key" (see PurchaseIntentAgentService#resolveWorkflowKey).
    // The next request for that same key picks the state back up and only
    // re-attempts the step that failed onward, instead of starting at step 1.
    // ==================================================================

    /** Persist a workflow's state (typically after failure) so it can be resumed later. */
    public void saveState(String workflowKey, PurchaseIntentAgentState state) {
        if (workflowKey == null || state == null) return;
        state.setWorkflowKey(workflowKey);
        state.setLastUpdated(LocalDateTime.now());
        workflowStateCache.put(workflowKey, state);
        log.info(
                "💾 Workflow state saved for key '{}' at step '{}'{}",
                workflowKey,
                state.getCurrentStep(),
                state.isError() ? " (errored)" : "");
    }

    /** Retrieve a previously saved workflow state, if any. */
    public Optional<PurchaseIntentAgentState> loadState(String workflowKey) {
        if (workflowKey == null) return Optional.empty();
        return Optional.ofNullable(workflowStateCache.getIfPresent(workflowKey));
    }

    /** Clear a saved workflow state - call on successful completion or explicit reset. */
    public void clearState(String workflowKey) {
        if (workflowKey == null) return;
        workflowStateCache.invalidate(workflowKey);
        log.info("🧹 Workflow state cleared for key '{}'", workflowKey);
    }

    /** True if there's a saved state for this key AND it ended in error (i.e. resumable). */
    public boolean hasFailedState(String workflowKey) {
        return loadState(workflowKey).map(PurchaseIntentAgentState::isError).orElse(false);
    }
}
