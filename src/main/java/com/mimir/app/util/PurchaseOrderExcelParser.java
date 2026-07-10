package com.mimir.app.util;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderExcelParser {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderExcelParser.class);

    private static final int COL_SL_NO = 0;
    private static final int COL_SUPPLIER_NAME = 1;
    private static final int COL_SUPPLIER_LOCATION = 2;
    private static final int COL_DIV = 3;
    private static final int COL_GROUP = 4;
    private static final int COL_DEPT = 5;
    private static final int COL_CLASS = 6;
    private static final int COL_SUB_CLASS = 7;
    private static final int COL_ITEM_STYLE = 8;
    private static final int COL_ITEM_DESCRIPTION = 9;
    private static final int COL_PRODUCT_GENERIC_NAME = 10;
    private static final int COL_UOM_NO = 11;
    private static final int COL_TAX_CATEGORY = 12;
    private static final int COL_PURCHASE_TYPE = 13;
    private static final int COL_BRAND = 14;
    private static final int COL_ITEM_MADE_WITH = 15;
    private static final int COL_HIT_DETAIL = 16;
    private static final int COL_HTS_NUMBER = 17;
    private static final int COL_COLOR_GROUP_CODE = 18;
    private static final int COL_ITEM_COLOR = 19;
    private static final int COL_UNIT_COST = 20;
    private static final int COL_UNIT_MRP = 21;
    private static final int COL_UOM = 22;
    private static final int COL_PREPACK = 23;

    // Size block start columns — labels are read dynamically from header row
    private static final int COL_CUT_QTY_START = 24;
    private static final int COL_BARCODE_START = 33;
    private static final int COL_PREPACK_RATIO_START = 42;
    private static final int COL_TOTAL_PREPACKS = 62;
    private static final int COL_TOTAL_QTY_PIECES = 63;
    private static final int COL_RDC_SIZES_START = 64;
    private static final int COL_TOTAL_PPK_QTY = 172;
    private static final int COL_TOTAL_LOOSE_QTY = 173;
    private static final int COL_TOTAL_ORDER_COM_QTY = 174;
    private static final int COL_TOTAL_ORDER_VALUE = 175;
    private static final int COL_DELIVERY_DATE = 176;
    private static final int COL_PO_NOT_BEFORE = 177;
    private static final int COL_PO_NOT_AFTER = 178;

    // ── Public API ──────────────────────────────────────────────────────────

    public List<Map<String, Object>> extractPurchaseIntentData(InputStream inputStream, String filename)
            throws Exception {

        List<Map<String, Object>> result = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            // Rows 1–5: sheet-level header
            Map<String, String> sheetHeader = extractSheetHeader(sheet);

            Row groupRow = sheet.getRow(6); // row 7: RDC group names
            Row headerRow = sheet.getRow(7); // row 8: column headers + size labels

            // Read size labels dynamically from the header row once —
            // all blocks (CUT QTY, BARCODE CM, PRE PACK, every RDC) share the same labels
            List<String> sizeLabels = extractSizeLabels(headerRow, COL_CUT_QTY_START);

            List<String> rdcNames = extractRdcNames(groupRow);

            log.info("Sheet header: {}", sheetHeader);
            log.info("Size labels detected: {}", sizeLabels);
            log.info("RDC names detected: {}", rdcNames);

            // Rows 9+: one record per data row
            for (int r = 8; r <= sheet.getLastRowNum(); r++) {

                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                String slNo = cellStr(row, COL_SL_NO);
                if (slNo == null || slNo.isBlank()) continue;

                Map<String, Object> record = new LinkedHashMap<>();

                record.put("file", filename);
                record.put("extractedAt", Instant.now().toString());
                record.put("source", "EXCEL");

                // Sheet-level header fields
                record.put("buyer", sheetHeader.get("BUYER"));
                record.put("otbBuyingPeriod", sheetHeader.get("OTB Buying Period"));
                record.put("paymentTerms", sheetHeader.get("PAYMENT TERMS"));
                record.put("dept", sheetHeader.get("DEPT"));
                record.put("season", sheetHeader.get("SEASON"));

                // Row-level item fields
                record.put("slNo", cellStr(row, COL_SL_NO));
                record.put("supplierName", cellStr(row, COL_SUPPLIER_NAME));
                record.put("supplierLocation", cellStr(row, COL_SUPPLIER_LOCATION));
                record.put("div", cellStr(row, COL_DIV));
                record.put("group", cellStr(row, COL_GROUP));
                record.put("itemDept", cellStr(row, COL_DEPT));
                record.put("class", cellStr(row, COL_CLASS));
                record.put("subClass", cellStr(row, COL_SUB_CLASS));
                record.put("styleNo", cellStr(row, COL_ITEM_STYLE));
                record.put("itemDescription", cellStr(row, COL_ITEM_DESCRIPTION));
                record.put("productGenericName", cellStr(row, COL_PRODUCT_GENERIC_NAME));
                record.put("uomNo", cellStr(row, COL_UOM_NO));
                record.put("taxCategory", cellStr(row, COL_TAX_CATEGORY));
                record.put("purchaseType", cellStr(row, COL_PURCHASE_TYPE));
                record.put("brand", cellStr(row, COL_BRAND));
                record.put("itemMadeWith", cellStr(row, COL_ITEM_MADE_WITH));
                record.put("hitDetail", cellStr(row, COL_HIT_DETAIL));
                record.put("htsNumber", cellStr(row, COL_HTS_NUMBER));
                record.put("colorGroupCode", cellStr(row, COL_COLOR_GROUP_CODE));
                record.put("itemColor", cellStr(row, COL_ITEM_COLOR));
                record.put("unitCost", cellStr(row, COL_UNIT_COST));
                record.put("unitMrp", cellStr(row, COL_UNIT_MRP));
                record.put("uom", cellStr(row, COL_UOM));
                record.put("prepack", cellStr(row, COL_PREPACK));

                // Size blocks — all use the same dynamic labels
                record.put("cutQuantities", extractSizeBlock(row, COL_CUT_QTY_START, sizeLabels));
                record.put("barcodeCm", extractSizeBlock(row, COL_BARCODE_START, sizeLabels));
                record.put("prePackARatio", extractSizeBlock(row, COL_PREPACK_RATIO_START, sizeLabels));

                record.put("totalPrepacks", cellStr(row, COL_TOTAL_PREPACKS));
                record.put("totalQtyInPieces", cellStr(row, COL_TOTAL_QTY_PIECES));
                record.put("rdcBreakdowns", extractRdcBreakdowns(row, rdcNames, sizeLabels));

                record.put("totalPpkQty", cellStr(row, COL_TOTAL_PPK_QTY));
                record.put("totalLooseQty", cellStr(row, COL_TOTAL_LOOSE_QTY));
                record.put("totalOrderComQty", cellStr(row, COL_TOTAL_ORDER_COM_QTY));
                record.put("totalOrderValue", cellStr(row, COL_TOTAL_ORDER_VALUE));

                record.put("deliveryDate", cellStr(row, COL_DELIVERY_DATE));
                record.put("poNotBefore", cellStr(row, COL_PO_NOT_BEFORE));
                record.put("poNotAfter", cellStr(row, COL_PO_NOT_AFTER));

                result.add(record);
            }
        }

        log.info("Excel parsing complete: file={} records={}", filename, result.size());
        return result;
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private Map<String, String> extractSheetHeader(Sheet sheet) {
        Map<String, String> header = new LinkedHashMap<>();
        for (int r = 0; r <= 4; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String label = cellStr(row, 0);
            String value = cellStr(row, 1);
            if (label != null && value != null) header.put(label, value);
        }
        return header;
    }

    // ── Size labels ──────────────────────────────────────────────────────────
    // Read from header row starting at the given column until a blank or
    // "TOTAL" cell is found. "TOTAL" is included as the last label.
    // Works for any season's size range (e.g. XS–XXL, 28–40, S–XL, etc.)

    private List<String> extractSizeLabels(Row headerRow, int startCol) {
        List<String> labels = new ArrayList<>();
        for (int col = startCol; col < headerRow.getLastCellNum(); col++) {
            String label = cellStr(headerRow, col);
            if (label == null || label.isBlank()) break;
            labels.add(label);
            if ("TOTAL".equalsIgnoreCase(label)) break;
        }
        return labels;
    }

    // ── RDC names ────────────────────────────────────────────────────────────

    private List<String> extractRdcNames(Row groupRow) {
        List<String> names = new ArrayList<>();
        if (groupRow == null) return names;
        int blockSize = 0; // will be set after first sizeLabels read — use fixed 9 as fallback
        for (int col = COL_RDC_SIZES_START; col < COL_TOTAL_PPK_QTY; col += 9) {
            String name = cellStr(groupRow, col);
            if (name != null && !name.isBlank()) names.add(name.trim());
        }
        return names;
    }

    // ── Size block extraction ─────────────────────────────────────────────────
    // Uses dynamic labels; skips entries where both label and value are blank/"-"

    private Map<String, Object> extractSizeBlock(Row row, int startCol, List<String> sizeLabels) {
        Map<String, Object> sizes = new LinkedHashMap<>();
        for (int i = 0; i < sizeLabels.size(); i++) {
            String label = sizeLabels.get(i);
            String value = cellStr(row, startCol + i);
            if (value != null && !value.isBlank()) {
                sizes.put(label, value);
            }
        }
        return sizes;
    }

    private Map<String, Object> extractRdcBreakdowns(Row row, List<String> rdcNames, List<String> sizeLabels) {
        Map<String, Object> rdcMap = new LinkedHashMap<>();
        int blockSize = sizeLabels.size(); // e.g. 9 for XS,S,M,L,XL,XXL,-,-,TOTAL
        for (int i = 0; i < rdcNames.size(); i++) {
            int blockStart = COL_RDC_SIZES_START + (i * blockSize);
            Map<String, Object> sizes = extractSizeBlock(row, blockStart, sizeLabels);
            if (!sizes.isEmpty()) rdcMap.put(rdcNames.get(i), sizes);
        }
        return rdcMap;
    }

    // ── Cell helpers ─────────────────────────────────────────────────────────

    private String cellStr(Row row, int col) {
        if (col >= row.getLastCellNum()) return null;
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC ->
                DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                        : formatNumeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield formatNumeric(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> null;
        };
    }

    private String formatNumeric(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }
}
