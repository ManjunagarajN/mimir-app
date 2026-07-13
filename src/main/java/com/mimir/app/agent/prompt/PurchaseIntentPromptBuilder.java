package com.mimir.app.agent.prompt;

import com.mimir.app.agent.domain.SearchCriteria;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Builds structured prompts for LLM interactions in the Purchase Intent workflow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseIntentPromptBuilder {

    private final ObjectMapper objectMapper;

    public String buildSearchCriteriaExtractionPrompt(String userQuery) {
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
                """.formatted(userQuery);
    }

    public String buildFieldOverridePrompt(SearchCriteria criteria) {
        try {
            String criteriaJson = objectMapper.writeValueAsString(criteria);
            return """
                    You are an expert JSON builder. The user wants to update specific fields in a Purchase Intent.
                    User requested changes (SearchCriteria): %s
                    
                    STRICT RULES:
                    1. Output a JSON object containing ONLY the fields that the user EXPLICITLY mentioned.
                    2. Map fields as follows:
                       - divName -> "division"
                       - groupName -> "groupName"
                       - deptName -> "department"
                       - style -> "style"
                       - season -> "hit"
                       - vendor -> "supplier"
                    3. Output ONLY raw JSON. No code, no markdown formatting.
                    4. All values MUST be Strings, not arrays.
                    5. NEVER output empty strings ("") or the word "null".
                    6. If a field is not mentioned, OMIT IT COMPLETELY from the output.
                    """.formatted(criteriaJson);
        } catch (Exception e) {
            log.error("Failed to build field override prompt: {}", e.getMessage());
            return "";
        }
    }

    public String buildWorkflowSummaryPrompt(
            String query,
            int createdPiCount,
            String createdPiNumbers,
            int fileSizeBytes) {
        
        return """
                Summarize the following Purchase Intent workflow execution:
                
                Original Query: '%s'
                PIs Created: %d
                PI Numbers: %s
                Downloaded File Size: %d bytes
                
                Provide a concise, professional summary of what was accomplished.
                """.formatted(query, createdPiCount, createdPiNumbers, fileSizeBytes);
    }
}