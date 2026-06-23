package com.mimir.app.util;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import net.sourceforge.tess4j.Tesseract;

@Component
public class PurchaseIntentPdfParser {
    private static final Logger log = LoggerFactory.getLogger(PurchaseIntentPdfParser.class);
    private static final int MIN_NATIVE_CHARS = 40;

    @Value("${ocr.tessdata:src/main/resources/tessdata}")
    private String tessdataPath;

    @Value("${ocr.lang:eng}")
    private String ocrLang;

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /** Convert a PDF InputStream into a structured Map ready for Jackson. */
    public List<Map<String, Object>> extractPurchaseIntentData(InputStream inputStream, String filename)
            throws Exception {

        Path tempPdf = Files.createTempFile("purchase-intent-", ".pdf");

        try {
            Files.copy(inputStream, tempPdf, StandardCopyOption.REPLACE_EXISTING);

            String text = extractTextFromPdf(tempPdf);

            log.info("Extracted {} characters from PDF: {}", text.length(), filename);

            Map<String, Object> header = new LinkedHashMap<>(extractPurchaseIntentHeader(text));

            List<String> notes = extractPurchaseIntentNotes(text);

            List<Map<String, Object>> result = new ArrayList<>();

            String[] pages = text.split("Purchase Intent Report Page\\s+\\d+\\s+of\\s+\\d+");

            for (String page : pages) {

                if (page == null || page.isBlank()) {
                    continue;
                }

                List<Map<String, Object>> pageItems = extractPurchaseIntentItems(page);

                Map<String, Object> delivery = extractDeliveryAndPricingDetails(page);

                log.info("Page contains {} item(s)", pageItems.size());

                for (Map<String, Object> item : pageItems) {

                    Map<String, Object> pageJson = new LinkedHashMap<>();

                    pageJson.put("file", filename);
                    pageJson.put("extractedAt", Instant.now().toString());

                    pageJson.put("header", header);
                    pageJson.put("notes", notes);

                    pageJson.put("item", item);

                    pageJson.put("delivery", delivery);

                    result.add(pageJson);
                }
            }

            log.info("Purchase Intent parsing completed for file: {} | records={}", filename, result.size());

            return result;

        } finally {

            Files.deleteIfExists(tempPdf);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Extraction (native text first, OCR fallback)
    // ─────────────────────────────────────────────────────────────────────

    private String extractTextFromPdf(Path pdf) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(doc);
            Tesseract tess = null;

            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String t = stripper.getText(doc);

                if (t == null || t.replaceAll("\\s+", "").length() < MIN_NATIVE_CHARS) {
                    if (tess == null) {
                        tess = new Tesseract();
                        tess.setDatapath(tessdataPath);
                        tess.setLanguage(ocrLang);
                    }
                    BufferedImage img = renderer.renderImageWithDPI(i, 300, ImageType.GRAY);
                    t = tess.doOCR(img);
                }
                sb.append(t).append('\n');
            }
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Parsing
    // ─────────────────────────────────────────────────────────────────────

    private String extractFieldValue(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private Map<String, String> extractPurchaseIntentHeader(String text) {
        String[] keys = {
            "Concept", "Department", "Order Type", "Currency", "Order Date",
            "Season", "Supplier", "Supplier Code", "Location", "COO"
        };
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : keys) {
            String v = extractFieldValue(text, Pattern.quote(k) + "\\s*[:\\-]\\s*([^\\r\\n]+)");
            if (v != null) out.put(k, v);
        }
        return out;
    }

    private List<String> extractPurchaseIntentNotes(String text) {
        List<String> notes = new ArrayList<>();
        Matcher m = Pattern.compile("Note:-\\s*([\\s\\S]*?)(?:Concept\\s*[:\\-])", Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (m.find()) {
            for (String line : m.group(1).split("\\r?\\n")) {
                String s = line.replaceAll("^[•\\s]+", "").trim();
                if (!s.isEmpty()) notes.add(s);
            }
        }
        return notes;
    }

    private List<Map<String, Object>> extractPurchaseIntentItems(String page) {

        List<Map<String, Object>> items = new ArrayList<>();

        // Extract dynamic size headers from page
        List<String> sizeHeaders = new ArrayList<>();

        Matcher sizeHeaderMatcher = Pattern.compile(
                        "Size\\s*Range\\s*SEASON\\s+(.*?)\\s+TOTAL", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(page);

        if (sizeHeaderMatcher.find()) {

            String headerText = sizeHeaderMatcher
                    .group(1)
                    .replaceAll("\\r?\\n", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            sizeHeaders.addAll(Arrays.stream(headerText.split("\\s+"))
                    .filter(s -> !s.equalsIgnoreCase("SEASON"))
                    .filter(s -> !s.equalsIgnoreCase("TOTAL"))
                    .toList());
        }

        String normalized =
                page.replaceAll("\\r?\\n", " ").replaceAll("\\s+", " ").trim();

        Pattern pattern = Pattern.compile(
                "(\\d{1,3},\\d{3})\\s+" + // PI NO
                        "(\\S+)\\s+"
                        + // STYLE
                        "(\\S+)\\s+"
                        + // BUYING COLOUR
                        "(\\S+)\\s+"
                        + // PO COLOR
                        "(\\S+)\\s+"
                        + // GROUP
                        "(.*?)\\s+"
                        + // Remaining text
                        "(S-\\d+)\\s+"
                        + // SEASON
                        "((?:[\\d,]+\\s+)+)"
                        + // Dynamic quantities
                        "([\\d,]+)" // TOTAL
                );

        Matcher matcher = pattern.matcher(normalized);

        while (matcher.find()) {

            Map<String, Object> item = new LinkedHashMap<>();

            item.put("piNo", matcher.group(1).replace(",", ""));
            item.put("styleNo", matcher.group(2));
            item.put("buyingColour", matcher.group(3));
            item.put("poColor", matcher.group(4));
            item.put("group", matcher.group(5));

            String remaining = matcher.group(6).trim();

            log.info("Remaining item text: {}", remaining);

            Matcher detailMatcher = Pattern.compile("^(.*?)\\s+" + "(.*?)\\s+" + "(.*?)\\s+" + "(.+?)\\s+" + "(XS.*)$")
                    .matcher(remaining);

            if (detailMatcher.find()) {

                item.put("dept", detailMatcher.group(1));
                item.put("class", detailMatcher.group(2));
                item.put("subClass", detailMatcher.group(3));
                item.put("productGenericName", detailMatcher.group(4));
                item.put("sizeRange", detailMatcher.group(5));
            }

            item.put("season", matcher.group(7));

            String quantityText = matcher.group(8).trim();

            List<String> quantities = Arrays.stream(quantityText.split("\\s+"))
                    .map(q -> q.replace(",", ""))
                    .toList();

            Map<String, String> sizes = new LinkedHashMap<>();

            for (int i = 0; i < Math.min(sizeHeaders.size(), quantities.size()); i++) {
                sizes.put(sizeHeaders.get(i), quantities.get(i));
            }

            item.put("sizes", sizes);

            item.put("total", matcher.group(9).replace(",", ""));

            items.add(item);
        }

        return items;
    }

    private Map<String, Object> extractDeliveryAndPricingDetails(String text) {
        Map<String, Object> delivery = new LinkedHashMap<>();
        String normalized =
                text.replaceAll("\\r?\\n", " ").replaceAll("\\s+", " ").trim();
        Pattern pattern = Pattern.compile(
                "([A-Z\\- ]+)\\s+" + // place
                        "(\\d{2}-[A-Za-z]{3}-\\d{4})\\s+"
                        + // booking date
                        "(\\d{2}-[A-Za-z]{3}-\\d{2})\\s+"
                        + // delivery date
                        "(.+?)\\s+"
                        + // item made with
                        "(INR|USD|EUR)\\s+"
                        + // currency
                        "([\\d.]+)\\s+"
                        + // cost
                        "([\\d.]+)\\s+"
                        + // mrp
                        "(.+)$" // buyer comments
                );

        Matcher matcher = pattern.matcher(normalized);
        if (matcher.find()) {
            delivery.put("placeOfDelivery", matcher.group(1).trim());
            delivery.put("bookingDate", matcher.group(2));
            delivery.put("deliveryDate", matcher.group(3));
            delivery.put("itemMadeWith", matcher.group(4).trim());
            delivery.put("currency", matcher.group(5));
            delivery.put("cost", matcher.group(6));
            delivery.put("mrp", matcher.group(7));
            delivery.put("buyerComments", matcher.group(8).trim());
        }
        log.info("Parsed delivery: {}", delivery);
        return delivery;
    }
}
