package com.mimir.app.util;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class PurchaseIntentDetector {

    // PDF Purchase Intent keywords
    private static final List<String> PDF_KEYWORDS = List.of(
            "CONCEPT",
            "DEPARTMENT",
            "ORDER TYPE",
            "CURRENCY",
            "ORDER DATE",
            "SEASON",
            "SUPPLIER",
            "SUPPLIER CODE",
            "LOCATION",
            "COO",
            "PI NO",
            "STYLE NO");

    // Excel Purchase Order keywords (from sheet header + column headers)
    private static final List<String> EXCEL_KEYWORDS = List.of(
            "BUYER",
            "OTB BUYING PERIOD",
            "PAYMENT TERMS",
            "SUPPLIER NAME",
            "SUPPLIER LOCATION",
            "CUT QUANTITY",
            "PRE PACK",
            "BARCODE CM",
            "PRODUCT GENERIC NAME",
            "COLOR GROUP CODE",
            "ITEM COLOR",
            "UNIT COST",
            "UNIT MRP",
            "TOTAL ORDER VALUE");

    private static final int MIN_MATCHES = 3;

    public boolean isPurchaseIntent(String text) {
        if (text == null || text.isBlank()) return false;
        String normalized = text.toUpperCase();
        return matchCount(normalized, PDF_KEYWORDS) >= MIN_MATCHES
                || matchCount(normalized, EXCEL_KEYWORDS) >= MIN_MATCHES;
    }

    public boolean isPurchaseIntentPdf(String text) {
        if (text == null || text.isBlank()) return false;
        return matchCount(text.toUpperCase(), PDF_KEYWORDS) >= MIN_MATCHES;
    }

    public boolean isPurchaseIntentExcel(String text) {
        if (text == null || text.isBlank()) return false;
        return matchCount(text.toUpperCase(), EXCEL_KEYWORDS) >= MIN_MATCHES;
    }

    private long matchCount(String normalizedText, List<String> keywords) {
        return keywords.stream().filter(normalizedText::contains).count();
    }
}
