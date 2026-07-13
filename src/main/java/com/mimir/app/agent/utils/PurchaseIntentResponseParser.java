package com.mimir.app.agent.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.agent.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Parses and validates LLM responses for Purchase Intent operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseIntentResponseParser {

    private final ObjectMapper objectMapper;
    private final JsonRepairService jsonRepairService;
    private final PurchaseIntentNormalizer normalizer;
    
    private static final DateTimeFormatter DATE_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SearchCriteria parseSearchCriteria(String llmResponse) {
        if (containsCodeHallucination(llmResponse)) {
            log.error("❌ LLM hallucinated code instead of JSON");
            return SearchCriteria.builder().build();
        }

        String cleanedJson = jsonRepairService.extractAndRepairJson(llmResponse);
        
        if (isEmptyJson(cleanedJson)) {
            log.warn("Empty JSON extracted from LLM response");
            return SearchCriteria.builder().build();
        }

        try {
            SearchCriteria criteria = objectMapper.readValue(cleanedJson, SearchCriteria.class);
            normalizer.normalizeDivision(criteria);
            normalizer.validateAndFix(criteria);
            
            log.info("✅ Successfully parsed SearchCriteria: {}", criteria);
            return criteria;
            
        } catch (Exception ex) {
            log.error("❌ Unable to parse LLM response. Raw: {}", llmResponse, ex);
            return SearchCriteria.builder().build();
        }
    }

    public MaxPurchaseIntentRequest parsePurchaseIntentRequest(String llmResponse) {
        if (containsCodeHallucination(llmResponse)) {
            return null;
        }

        String cleanedJson = jsonRepairService.extractAndRepairJson(llmResponse);
        
        if (isEmptyJson(cleanedJson)) {
            return null;
        }

        try {
            MaxPurchaseIntentRequest request = objectMapper.readValue(cleanedJson, MaxPurchaseIntentRequest.class);
            ensureRequiredFields(request);
            return request;
            
        } catch (Exception ex) {
            log.error("❌ Unable to parse create request from LLM. Raw: {}", llmResponse, ex);
            return null;
        }
    }

    private boolean containsCodeHallucination(String response) {
        return response != null && 
               (response.contains("function ") || response.contains("const "));
    }

    private boolean isEmptyJson(String json) {
        return json == null || json.isBlank() || json.equals("{}");
    }

    private void ensureRequiredFields(MaxPurchaseIntentRequest request) {
        if (request.getIdentity() == null) {
            request.setIdentity(createDefaultIdentity());
        }
        if (request.getSourceSnapshot() == null) {
            request.setSourceSnapshot(createDefaultSourceSnapshot());
        }
        if (request.getWorkflowCommand() == null) {
            request.setWorkflowCommand(createDefaultWorkflowCommand());
        }
    }

    private IntentRequest.Identity createDefaultIdentity() {
        var identity = new IntentRequest.Identity();
        identity.setRefId(UUID.randomUUID().toString());
        identity.setPurchaseIntentNo(2001L);
        identity.setPurchaseOrderNo(0L);
        return identity;
    }

    private IntentRequest.SourceSnapshot createDefaultSourceSnapshot() {
        var snapshot = new IntentRequest.SourceSnapshot();
        snapshot.setRowHash(UUID.randomUUID().toString());
        snapshot.setLastUpdatedAmendAt(LocalDateTime.now().format(DATE_FORMATTER));
        snapshot.setLastUpdatedAmendBy("agent-auto");
        snapshot.setIsAmended(false);
        return snapshot;
    }

    private IntentRequest.WorkflowCommand createDefaultWorkflowCommand() {
        var command = new IntentRequest.WorkflowCommand();
        command.setEvent("CREATE");
        command.setAllocationRequestedBy("");
        command.setAllocationRequestedAt("");
        command.setIsAllocationRequested(false);
        return command;
    }
}