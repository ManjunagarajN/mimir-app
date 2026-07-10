package com.mimir.app.agent.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.mimir.app.agent.domain.IntentRequest;
import com.mimir.app.agent.domain.MaxPurchaseIntentRequest;
import com.mimir.app.agent.domain.SearchCriteria;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mimir.app.agent.domain.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaParser {

    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String buildAnalysisPrompt(String query) {
        return """
                You are a strict JSON extraction API. Extract ONLY the values explicitly mentioned by the user.
                NEVER write code. NEVER write explanations. Return ONLY valid JSON starting with '{' and ending with '}'.

                CRITICAL RULES:
                1. Map the value IMMEDIATELY AFTER each keyword to that field.
                2. "division" keyword -> value goes to `divName`
                3. "department" or "dept" keyword -> value goes to `deptName` (Array of Strings)
                4. "style" keyword -> value goes to `style` (Array of Strings)
                5. "group" keyword -> value goes to `groupName` (Array of Strings)
                6. "season" or "size" keyword -> value goes to `season`
                7. "supplier" or "vendor" keyword -> value goes to `vendor`
                8. DO NOT extract keywords themselves as values.
                9. If a field is not mentioned, set it to null.
                10. DIVISION NORMALIZATION: "womens wear" -> "Max-Women Wear", "innerwear" -> "Max-Innerwear"

                EXAMPLE:
                Query: "Create a PI for division Max-Women Wear department WW DENIM group COLLECTION, BASIC, CATEGORY style ELA4D"
                Output:
                {
                  "divName": "Max-Women Wear",
                  "deptName": ["WW DENIM"],
                  "style": ["ELA4D"],
                  "groupName": ["COLLECTION", "BASIC", "CATEGORY"],
                  "season": null,
                  "vendor": null
                }

                NOW EXTRACT FROM THIS QUERY:
                "%s"
                """.formatted(query);
    }

    public SearchCriteria parseAnalysis(String response) {
        try {
            if (response != null && (response.contains("function ") || response.contains("const "))) {
                log.error("❌ LLM hallucinated code instead of JSON");
                return SearchCriteria.builder().build();
            }

            String json = extractAndFixJson(response);
            if (json == null || json.equals("{}")) {
                log.warn("Empty JSON extracted from LLM response.");
                return SearchCriteria.builder().build();
            }

            SearchCriteria parsed = objectMapper.readValue(json, SearchCriteria.class);
            normalizeDivision(parsed);
            validateAndFix(parsed);

            log.info("✅ Successfully parsed SearchCriteria: {}", parsed);
            return parsed;
        } catch (Exception ex) {
            log.error("❌ Unable to parse LLM response. Raw: {}", response, ex);
            return SearchCriteria.builder().build();
        }
    }

    private void validateAndFix(SearchCriteria criteria) {
        if (criteria.getVendor() != null && criteria.getVendor().equalsIgnoreCase("division")) {
            criteria.setVendor(null);
        }
        if (criteria.getSeason() != null
                && (criteria.getSeason().equalsIgnoreCase("PI")
                        || criteria.getSeason().equalsIgnoreCase("Purchase Intent"))) {
            criteria.setSeason(null);
        }
        if (criteria.getDeptName() != null) {
            criteria.setDeptName(
                    criteria.getDeptName().stream().map(String::toUpperCase).toList());
        }
        if (criteria.getStyle() != null) {
            criteria.setStyle(
                    criteria.getStyle().stream().map(String::toUpperCase).toList());
        }
        if (criteria.getGroupName() != null) {
            criteria.setGroupName(
                    criteria.getGroupName().stream().map(String::toUpperCase).toList());
        }
    }

    public String buildCreateRequestPrompt(String query, String existingRefId, Long existingPiNo) {
        return """
                You are an expert Purchase Intent data builder. Build a complete MaxPurchaseIntentRequest JSON.
                User Query: "%s"
                Existing RefId: %s
                Existing PI Number: %s
                Return ONLY the complete JSON object.
                """.formatted(
                        query,
                        existingRefId != null ? existingRefId : "null",
                        existingPiNo != null ? existingPiNo.toString() : "null");
    }

    public MaxPurchaseIntentRequest parseCreateRequest(String response) {
        try {
            if (response != null && (response.contains("function ") || response.contains("const "))) {
                return null;
            }
            String json = extractAndFixJson(response);
            if (json == null || json.equals("{}")) return null;

            MaxPurchaseIntentRequest request = objectMapper.readValue(json, MaxPurchaseIntentRequest.class);
            if (request.getIdentity() == null) {
                IntentRequest.Identity identity = new IntentRequest.Identity();
                identity.setRefId(UUID.randomUUID().toString());
                identity.setPurchaseIntentNo(2001L);
                identity.setPurchaseOrderNo(0L);
                request.setIdentity(identity);
            }
            if (request.getSourceSnapshot() == null) {
                IntentRequest.SourceSnapshot snapshot = new IntentRequest.SourceSnapshot();
                snapshot.setRowHash(UUID.randomUUID().toString());
                snapshot.setLastUpdatedAmendAt(LocalDateTime.now().format(DATE_FORMATTER));
                snapshot.setLastUpdatedAmendBy("agent-auto");
                snapshot.setIsAmended(false);
                request.setSourceSnapshot(snapshot);
            }
            if (request.getWorkflowCommand() == null) {
                IntentRequest.WorkflowCommand command = new IntentRequest.WorkflowCommand();
                command.setEvent("CREATE");
                command.setAllocationRequestedBy("");
                command.setAllocationRequestedAt("");
                command.setIsAllocationRequested(false);
                request.setWorkflowCommand(command);
            }
            return request;
        } catch (Exception ex) {
            log.error("❌ Unable to parse create request from LLM. Raw: {}", response, ex);
            return null;
        }
    }

    private void normalizeDivision(SearchCriteria criteria) {
        if (criteria.getDivName() != null) {
            String div = criteria.getDivName().toUpperCase().trim();
            if (div.contains("WOMEN") || div.equals("WW")) criteria.setDivName("Max-Women Wear");
            else if (div.contains("INNER")) criteria.setDivName("Max-Innerwear");
        }
    }

    // 🆕 ROBUST JSON REPAIR: Automatically closes truncated JSON from LLM
    public String extractAndFixJson(String response) {
        if (response == null || response.isBlank()) return "{}";

        String cleaned =
                response.replaceAll("```json", "").replaceAll("```", "").trim();

        int keyIndex = cleaned.indexOf("\"divName\"");
        if (keyIndex == -1) keyIndex = cleaned.indexOf("\"maxPurchaseLineItems\"");

        int start = -1;
        if (keyIndex != -1) {
            start = cleaned.lastIndexOf('{', keyIndex);
        } else {
            start = cleaned.indexOf('{');
        }

        if (start == -1) return "{}";

        String jsonCandidate = cleaned.substring(start);

        // Count braces and brackets to detect truncation
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < jsonCandidate.length(); i++) {
            char c = jsonCandidate.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
                else if (c == '[') openBrackets++;
                else if (c == ']') openBrackets--;
            }
        }

        StringBuilder sb = new StringBuilder(jsonCandidate);

        // 1. If we are inside an unclosed string, close it
        if (inString) {
            sb.append('"');
        }

        // 2. Close any open arrays first
        while (openBrackets > 0) {
            sb.append(']');
            openBrackets--;
        }

        // 3. Close any open objects
        while (openBraces > 0) {
            sb.append('}');
            openBraces--;
        }

        // 4. Remove any trailing commas before the closing braces/brackets
        String fixedJson = sb.toString();
        fixedJson = fixedJson.replaceAll(",\\s*([}\\]])", "$1");

        return fixedJson;
    }
}
