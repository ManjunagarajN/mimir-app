package com.mimir.app.agent.utils;

import com.mimir.app.agent.domain.SearchCriteria;
import com.mimir.app.agent.prompt.PurchaseIntentPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Facade for LLM operations in Purchase Intent workflow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaParser {

    private final PurchaseIntentPromptBuilder promptBuilder;
    private final PurchaseIntentResponseParser responseParser;
    private final JsonRepairService jsonRepairService;

    public String buildAnalysisPrompt(String query) {
        return promptBuilder.buildSearchCriteriaExtractionPrompt(query);
    }

    public SearchCriteria parseAnalysis(String response) {
        return responseParser.parseSearchCriteria(response);
    }

    public String buildFieldOverridePrompt(SearchCriteria criteria) {
        return promptBuilder.buildFieldOverridePrompt(criteria);
    }

    public String buildWorkflowSummaryPrompt(String query, int piCount, String piNumbers, int fileSize) {
        return promptBuilder.buildWorkflowSummaryPrompt(query, piCount, piNumbers, fileSize);
    }

    public String extractAndFixJson(String response) {
        return jsonRepairService.extractAndRepairJson(response);
    }
}