package com.mimir.app.agent.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Repairs malformed or truncated JSON from LLM responses.
 */
@Slf4j
@Component
public class JsonRepairService {

    private static final String EMPTY_JSON = "{}";

    public String extractAndRepairJson(String response) {
        if (response == null || response.isBlank()) {
            return EMPTY_JSON;
        }

        String cleaned = removeMarkdownFormatting(response);
        String jsonCandidate = extractJsonObject(cleaned);
        
        if (jsonCandidate == null) {
            return EMPTY_JSON;
        }

        return repairJson(jsonCandidate);
    }

    private String removeMarkdownFormatting(String response) {
        return response
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
    }

    private String extractJsonObject(String cleaned) {
        int keyIndex = findJsonStartKey(cleaned);
        int startIndex = findOpeningBrace(cleaned, keyIndex);
        
        if (startIndex == -1) {
            return null;
        }
        
        return cleaned.substring(startIndex);
    }

    private int findJsonStartKey(String text) {
        int index = text.indexOf("\"divName\"");
        if (index == -1) {
            index = text.indexOf("\"maxPurchaseLineItems\"");
        }
        return index;
    }

    private int findOpeningBrace(String text, int keyIndex) {
        if (keyIndex != -1) {
            return text.lastIndexOf('{', keyIndex);
        }
        return text.indexOf('{');
    }

    private String repairJson(String jsonCandidate) {
        JsonStructureAnalysis analysis = analyzeStructure(jsonCandidate);
        
        StringBuilder repaired = new StringBuilder(jsonCandidate);
        
        closeUnclosedString(repaired, analysis.inString());
        closeUnclosedBrackets(repaired, analysis.openBrackets());
        closeUnclosedBraces(repaired, analysis.openBraces());
        
        return removeTrailingCommas(repaired.toString());
    }

    private JsonStructureAnalysis analyzeStructure(String json) {
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
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
                openBraces += (c == '{') ? 1 : (c == '}') ? -1 : 0;
                openBrackets += (c == '[') ? 1 : (c == ']') ? -1 : 0;
            }
        }

        return new JsonStructureAnalysis(openBraces, openBrackets, inString);
    }

    private void closeUnclosedString(StringBuilder sb, boolean inString) {
        if (inString) {
            sb.append('"');
        }
    }

    private void closeUnclosedBrackets(StringBuilder sb, int count) {
        while (count > 0) {
            sb.append(']');
            count--;
        }
    }

    private void closeUnclosedBraces(StringBuilder sb, int count) {
        while (count > 0) {
            sb.append('}');
            count--;
        }
    }

    private String removeTrailingCommas(String json) {
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }

    private record JsonStructureAnalysis(int openBraces, int openBrackets, boolean inString) {}
}