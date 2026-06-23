package com.mimir.app.util;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class PurchaseIntentDetector {

    private static final List<String> PI_KEYWORDS = List.of(
            "Concept",
            "Department",
            "Order Type",
            "Currency",
            "Order Date",
            "Season",
            "Supplier",
            "Supplier Code",
            "Location",
            "COO",
            "PI NO",
            "STYLE NO");

    public boolean isPurchaseIntent(String text) {

        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = text.toUpperCase();

        long matches = PI_KEYWORDS.stream().filter(normalized::contains).count();

        return matches >= 3;
    }
}
