package com.mimir.app.util.parser;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.mimir.app.util.PurchaseIntentDetector;
import com.mimir.app.util.PurchaseIntentPdfParser;
import com.mimir.app.util.PurchaseIntentPdfPromptBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfDocumentParser implements DocumentParser {

    private final Tika tika;
    private final PurchaseIntentDetector purchaseIntentDetector;
    private final PurchaseIntentPdfParser purchaseIntentPdfParser;
    private final PurchaseIntentPdfPromptBuilder purchaseIntentPdfTextGenerator;

    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    @Override
    public List<Map<String, Object>> parse(byte[] fileBytes, String filename) throws Exception {

        // First pass: Tika for plain-text extraction + PI detection
        String tikaText =
                tika.parseToString(new ByteArrayInputStream(fileBytes)).trim();

        if (!purchaseIntentDetector.isPurchaseIntentPdf(tikaText)) {
            log.info("Plain PDF detected: {}", filename);
            // Wrap tikaText in a generic record so generateRetrievalText can pass it through
            return List.of(Map.of("_source", "PDF_PLAIN", "_text", tikaText));
        }

        log.info("Purchase Intent PDF detected: {}", filename);

        // Second pass: structured parser on a fresh stream
        return purchaseIntentPdfParser.extractPurchaseIntentData(new ByteArrayInputStream(fileBytes), filename);
    }

    @Override
    public String generateRetrievalText(List<Map<String, Object>> records) {

        // Plain PDF fallback — records contain raw tika text
        if (!records.isEmpty() && "PDF_PLAIN".equals(records.get(0).get("_source"))) {
            return (String) records.get(0).get("_text");
        }

        return purchaseIntentPdfTextGenerator.generateRetrievalText(records);
    }
}
