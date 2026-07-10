package com.mimir.app.util;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("unchecked")
public class PurchaseOrderExcelPromptBuilder {

    public String generateRetrievalText(List<Map<String, Object>> purchaseIntents) {

        if (purchaseIntents == null || purchaseIntents.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        for (Map<String, Object> record : purchaseIntents) {
            if ("EXCEL".equals(record.get("source"))) {
                appendExcelRecord(record, sb);
            } else {
                appendPdfRecord(record, sb);
            }
            sb.append("\n===================================================\n\n");
        }

        return sb.toString();
    }

    // ── Excel ────────────────────────────────────────────────────────────────

    private void appendExcelRecord(Map<String, Object> r, StringBuilder sb) {

        // ── Order context ─────────────────────────────────────────────────
        String buyer = str(r.get("buyer"));
        String season = str(r.get("season"));
        String dept = str(r.get("dept"));
        String otb = str(r.get("otbBuyingPeriod"));
        String payment = str(r.get("paymentTerms"));

        if (anyPresent(buyer, season, dept)) {
            sb.append("This purchase order");
            appendPhrase(sb, " was raised by buyer", buyer);
            appendPhrase(sb, " for season", season);
            appendPhrase(sb, " under department", dept);
            appendPhrase(sb, " with OTB buying period", otb);
            appendPhrase(sb, " on payment terms", payment);
            sb.append(".\n\n");
        }

        // ── Item identity ─────────────────────────────────────────────────
        String slNo = str(r.get("slNo"));
        String style = str(r.get("styleNo"));
        String desc = str(r.get("itemDescription"));
        String pgn = str(r.get("productGenericName"));
        String brand = str(r.get("brand"));
        String colour = str(r.get("itemColor"));
        String cgc = str(r.get("colorGroupCode"));
        String group = str(r.get("group"));
        String cls = str(r.get("class"));
        String subCls = str(r.get("subClass"));
        String itemDpt = str(r.get("itemDept"));
        String div = str(r.get("div"));

        if (anyPresent(slNo, style, desc)) {
            sb.append("Item serial number ").append(nvl(slNo, "N/A"));
            appendPhrase(sb, " has style number", style);
            appendPhrase(sb, " described as", desc);
            appendPhrase(sb, " with product generic name", pgn);
            appendPhrase(sb, " under brand", brand);
            sb.append(".\n");
        }

        if (anyPresent(colour, cgc, group, cls, subCls)) {
            sb.append("The item colour is ").append(nvl(colour, "unspecified"));
            appendPhrase(sb, " with colour group code", cgc);
            appendPhrase(sb, " belonging to division", div);
            appendPhrase(sb, ", group", group);
            appendPhrase(sb, ", department", itemDpt);
            appendPhrase(sb, ", class", cls);
            appendPhrase(sb, " and sub class", subCls);
            sb.append(".\n");
        }

        // ── Supplier ──────────────────────────────────────────────────────
        String supplier = str(r.get("supplierName"));
        String supLoc = str(r.get("supplierLocation"));
        String purType = str(r.get("purchaseType"));
        String itemMade = str(r.get("itemMadeWith"));
        String hts = str(r.get("htsNumber"));
        String taxCat = str(r.get("taxCategory"));

        if (anyPresent(supplier, supLoc)) {
            sb.append("The supplier is ").append(nvl(supplier, "unspecified"));
            appendPhrase(sb, " located at", supLoc);
            appendPhrase(sb, " with purchase type", purType);
            appendPhrase(sb, ". The item is made with", itemMade);
            appendPhrase(sb, " and has HTS number", hts);
            appendPhrase(sb, " under tax category", taxCat);
            sb.append(".\n");
        }

        // ── Pricing ───────────────────────────────────────────────────────
        String cost = str(r.get("unitCost"));
        String mrp = str(r.get("unitMrp"));
        String uom = str(r.get("uom"));
        String ppk = str(r.get("prepack"));

        if (anyPresent(cost, mrp)) {
            sb.append("The unit cost is ").append(nvl(cost, "N/A"));
            appendPhrase(sb, " and the MRP is", mrp);
            appendPhrase(sb, " per", uom);
            appendPhrase(sb, ". Prepack flag is", ppk);
            sb.append(".\n");
        }

        sb.append("\n");

        // ── Cut quantities ────────────────────────────────────────────────
        Map<String, Object> cutQty = (Map<String, Object>) r.get("cutQuantities");
        if (cutQty != null && !cutQty.isEmpty()) {
            sb.append("The cut quantities by size for style ")
                    .append(nvl(style, slNo))
                    .append(" are: ");
            appendSizeMapProse(cutQty, sb);
            sb.append("\n");
        }

        // ── Pre-pack ratio ────────────────────────────────────────────────
        Map<String, Object> ppkRatio = (Map<String, Object>) r.get("prePackARatio");
        if (ppkRatio != null && !ppkRatio.isEmpty()) {
            sb.append("The pre-pack A ratio for style ")
                    .append(nvl(style, slNo))
                    .append(" is: ");
            appendSizeMapProse(ppkRatio, sb);
            sb.append("\n");
        }

        // ── Totals ────────────────────────────────────────────────────────
        String totalPrepacks = str(r.get("totalPrepacks"));
        String totalPieces = str(r.get("totalQtyInPieces"));
        String totalPpkQty = str(r.get("totalPpkQty"));
        String totalLoose = str(r.get("totalLooseQty"));
        String totalOrderQty = str(r.get("totalOrderComQty"));
        String totalOrderVal = str(r.get("totalOrderValue"));

        if (anyPresent(totalOrderQty, totalOrderVal)) {
            sb.append("The total order combined quantity is ").append(nvl(totalOrderQty, "N/A"));
            appendPhrase(sb, " with a total order value of", totalOrderVal);
            appendPhrase(sb, ". Total PPK quantity is", totalPpkQty);
            appendPhrase(sb, ", total loose quantity is", totalLoose);
            appendPhrase(sb, ", total prepacks is", totalPrepacks);
            appendPhrase(sb, " and total pieces is", totalPieces);
            sb.append(".\n\n");
        }

        // ── RDC breakdowns ────────────────────────────────────────────────
        Map<String, Object> rdcBreakdowns = (Map<String, Object>) r.get("rdcBreakdowns");
        if (rdcBreakdowns != null && !rdcBreakdowns.isEmpty()) {
            sb.append("The quantity breakdown by distribution centre for style ")
                    .append(nvl(style, slNo))
                    .append(" is as follows:\n");
            for (Map.Entry<String, Object> rdc : rdcBreakdowns.entrySet()) {
                Map<String, Object> rdcSizes = (Map<String, Object>) rdc.getValue();
                sb.append("  ").append(rdc.getKey()).append(" — ");
                appendSizeMapProse(rdcSizes, sb);
            }
            sb.append("\n");
        }

        // ── Delivery ──────────────────────────────────────────────────────
        String deliveryDate = str(r.get("deliveryDate"));
        String notBefore = str(r.get("poNotBefore"));
        String notAfter = str(r.get("poNotAfter"));

        if (anyPresent(deliveryDate, notBefore, notAfter)) {
            sb.append("Delivery is scheduled for ").append(nvl(deliveryDate, "an unspecified date"));
            appendPhrase(sb, ". The PO window is not before", notBefore);
            appendPhrase(sb, " and not after", notAfter);
            sb.append(".\n");
        }
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    private void appendPdfRecord(Map<String, Object> json, StringBuilder sb) {

        // ── Header ────────────────────────────────────────────────────────
        Map<String, Object> header = (Map<String, Object>) json.get("header");
        if (header != null && !header.isEmpty()) {
            String concept = str(header.get("Concept"));
            String dept = str(header.get("Department"));
            String orderType = str(header.get("Order Type"));
            String orderDate = str(header.get("Order Date"));
            String season = str(header.get("Season"));
            String supplier = str(header.get("Supplier"));
            String supCode = str(header.get("Supplier Code"));
            String location = str(header.get("Location"));
            String coo = str(header.get("COO"));
            String currency = str(header.get("Currency"));

            sb.append("This purchase intent belongs to division ").append(nvl(concept, "unspecified"));
            appendPhrase(sb, ", department", dept);
            appendPhrase(sb, ". The order type is", orderType);
            appendPhrase(sb, " dated", orderDate);
            appendPhrase(sb, " for season", season);
            sb.append(".\n");

            if (anyPresent(supplier, supCode, location, coo)) {
                sb.append("The supplier is ").append(nvl(supplier, "unspecified"));
                appendPhrase(sb, " with supplier code", supCode);
                appendPhrase(sb, " located at", location);
                appendPhrase(sb, ". Country of origin is", coo);
                appendPhrase(sb, ". Currency is", currency);
                sb.append(".\n");
            }
            sb.append("\n");
        }

        // ── Item ──────────────────────────────────────────────────────────
        Map<String, Object> item = (Map<String, Object>) json.get("item");
        if (item != null && !item.isEmpty()) {
            String piNo = str(item.get("piNo"));
            String style = str(item.get("styleNo"));
            String bColour = str(item.get("buyingColour"));
            String poColour = str(item.get("poColor"));
            String group = str(item.get("group"));
            String dept = str(item.get("dept"));
            String cls = str(item.get("class"));
            String subCls = str(item.get("subClass"));
            String pgn = str(item.get("productGenericName"));
            String szRange = str(item.get("sizeRange"));
            String season = str(item.get("season"));
            String total = str(item.get("total"));

            sb.append("Purchase intent number ").append(nvl(piNo, "N/A"));
            appendPhrase(sb, " has style number", style);
            appendPhrase(sb, ". The buying colour is", bColour);
            appendPhrase(sb, " and PO colour is", poColour);
            sb.append(".\n");

            sb.append("The item belongs to group ").append(nvl(group, "unspecified"));
            appendPhrase(sb, ", department", dept);
            appendPhrase(sb, ", class", cls);
            appendPhrase(sb, " and sub class", subCls);
            appendPhrase(sb, ". Product generic name is", pgn);
            appendPhrase(sb, ". Size range is", szRange);
            appendPhrase(sb, " for season", season);
            sb.append(".\n");

            sb.append("The total quantity for purchase intent number ")
                    .append(nvl(piNo, "N/A"))
                    .append(" is ")
                    .append(nvl(total, "unspecified"))
                    .append(".\n");

            Map<String, Object> sizes = (Map<String, Object>) item.get("sizes");
            if (sizes != null && !sizes.isEmpty()) {
                sb.append("Size-wise quantities for purchase intent ")
                        .append(piNo)
                        .append(" are: ");
                appendSizeMapProse(sizes, sb);
            }
            sb.append("\n");
        }

        // ── Delivery ──────────────────────────────────────────────────────
        Map<String, Object> delivery = (Map<String, Object>) json.get("delivery");
        if (delivery != null && !delivery.isEmpty()) {
            String place = str(delivery.get("placeOfDelivery"));
            String bookDate = str(delivery.get("bookingDate"));
            String delDate = str(delivery.get("deliveryDate"));
            String madeWith = str(delivery.get("itemMadeWith"));
            String fabric = str(delivery.get("fabricConstruction"));
            String currency = str(delivery.get("currency"));
            String cost = str(delivery.get("cost"));
            String mrp = str(delivery.get("mrp"));
            String comments = str(delivery.get("buyerComments"));

            sb.append("The place of delivery is ").append(nvl(place, "unspecified"));
            appendPhrase(sb, ". Booking date is", bookDate);
            appendPhrase(sb, " and delivery date is", delDate);
            sb.append(".\n");

            if (anyPresent(madeWith, fabric, currency, cost, mrp)) {
                sb.append("The item is made with ").append(nvl(madeWith, "unspecified material"));
                appendPhrase(sb, " with fabric construction", fabric);
                appendPhrase(sb, ". Currency is", currency);
                appendPhrase(sb, ", cost is", cost);
                appendPhrase(sb, " and MRP is", mrp);
                sb.append(".\n");
            }

            if (comments != null && !comments.isBlank()) {
                sb.append("Buyer comments: ").append(comments).append(".\n");
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Appends " prefixText value" only when value is non-null and non-blank. */
    private void appendPhrase(StringBuilder sb, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(prefix).append(" ").append(value);
        }
    }

    /** Appends size map as "XS: 973, S: 1642, M: 2105, TOTAL: 8422.\n" */
    private void appendSizeMapProse(Map<String, Object> sizes, StringBuilder sb) {
        boolean first = true;
        for (Map.Entry<String, Object> e : sizes.entrySet()) {
            if ("-".equals(e.getKey())) continue; // skip blank size columns
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
            first = false;
        }
        sb.append(".\n");
    }

    private boolean anyPresent(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return true;
        }
        return false;
    }

    /** Returns value if non-null/non-blank, otherwise the fallback. */
    private String nvl(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private String str(Object value) {
        return value == null ? null : value.toString().trim();
    }
}
