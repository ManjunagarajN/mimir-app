package com.mimir.app.util.parser;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import com.mimir.app.util.PurchaseIntentDetector;
import com.mimir.app.util.PurchaseOrderExcelParser;
import com.mimir.app.util.PurchaseOrderExcelPromptBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelDocumentParser implements DocumentParser {

    private static final Set<String> SUPPORTED = Set.of("xlsx", "xls");
    private final Tika tika;
    private final PurchaseOrderExcelParser purchaseOrderExcelParser;
    private final PurchaseIntentDetector purchaseIntentDetector;
    private final PurchaseOrderExcelPromptBuilder purchaseOrderExcelTextGenerator;

    @Override
    public boolean supports(String extension) {
        return SUPPORTED.contains(extension.toLowerCase());
    }

    @Override
    public List<Map<String, Object>> parse(byte[] fileBytes, String filename) throws Exception {

        // Optional guard: verify it's actually a purchase order Excel
        String tikaText =
                tika.parseToString(new ByteArrayInputStream(fileBytes)).trim();

        if (!purchaseIntentDetector.isPurchaseIntentExcel(tikaText)) {
            log.warn("Excel file does not appear to be a Purchase Order, falling back to plain text: {}", filename);
            return List.of(Map.of("_source", "EXCEL_PLAIN", "_text", tikaText));
        }

        log.info("Purchase Order Excel detected: {}", filename);
        return purchaseOrderExcelParser.extractPurchaseIntentData(new ByteArrayInputStream(fileBytes), filename);
    }

    @Override
    public String generateRetrievalText(List<Map<String, Object>> records) {
        if (!records.isEmpty() && "EXCEL_PLAIN".equals(records.get(0).get("_source"))) {
            return (String) records.get(0).get("_text");
        }
        return purchaseOrderExcelTextGenerator.generateRetrievalText(records);
    }
}
