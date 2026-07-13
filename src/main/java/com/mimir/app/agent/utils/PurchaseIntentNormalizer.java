package com.mimir.app.agent.utils;

import com.mimir.app.agent.domain.SearchCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Normalizes and validates Purchase Intent data extracted from LLM responses.
 */
@Slf4j
@Component
public class PurchaseIntentNormalizer {

    private static final String DIVISION_WOMEN_WEAR = "Max-Women Wear";
    private static final String DIVISION_INNERWEAR = "Max-Innerwear";

    public void normalizeDivision(SearchCriteria criteria) {
        if (criteria.getDivName() == null) {
            return;
        }

        String normalizedDivision = switch (criteria.getDivName().toUpperCase().trim()) {
            case String div when div.contains("WOMEN") || div.equals("WW") -> DIVISION_WOMEN_WEAR;
            case String div when div.contains("INNER") -> DIVISION_INNERWEAR;
            default -> criteria.getDivName();
        };

        criteria.setDivName(normalizedDivision);
    }

    public void validateAndFix(SearchCriteria criteria) {
        fixInvalidVendor(criteria);
        fixInvalidSeason(criteria);
        normalizeTextFields(criteria);
    }

    private void fixInvalidVendor(SearchCriteria criteria) {
        if ("division".equalsIgnoreCase(criteria.getVendor())) {
            log.warn("⚠️ LLM incorrectly extracted 'division' as vendor - setting to null");
            criteria.setVendor(null);
        }
    }

    private void fixInvalidSeason(SearchCriteria criteria) {
        if (criteria.getSeason() != null) {
            String season = criteria.getSeason();
            if ("PI".equalsIgnoreCase(season) || "Purchase Intent".equalsIgnoreCase(season)) {
                log.warn("⚠️ LLM incorrectly extracted '{}' as season - setting to null", season);
                criteria.setSeason(null);
            }
        }
    }

    private void normalizeTextFields(SearchCriteria criteria) {
        criteria.setDeptName(normalizeList(criteria.getDeptName()));
        criteria.setStyle(normalizeList(criteria.getStyle()));
        criteria.setGroupName(normalizeList(criteria.getGroupName()));
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(String::toUpperCase)
                .toList();
    }
}