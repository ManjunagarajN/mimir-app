package com.mimir.app.util;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PurchaseIntentTextGenerator {

    @SuppressWarnings("unchecked")
    public String generateRetrievalText(List<Map<String, Object>> purchaseIntents) {

        StringBuilder sb = new StringBuilder();

        if (purchaseIntents == null || purchaseIntents.isEmpty()) {
            return "";
        }

        for (Map<String, Object> json : purchaseIntents) {

            appendPurchaseIntentHeader(json, sb);
            appendPurchaseIntentNotes(json, sb);
            appendPurchaseIntentItem(json, sb);
            appendDeliveryDetails(json, sb);

            sb.append("\n");
            sb.append("===================================================\n\n");
        }

        return sb.toString();
    }

    private void appendPurchaseIntentHeader(Map<String, Object> json, StringBuilder sb) {

        Map<String, Object> header = (Map<String, Object>) json.get("header");

        if (header == null || header.isEmpty()) {
            return;
        }

        sb.append("""
                Purchase intent belongs to Division %s.
                Department is %s.
                Order type is %s.
                Order date is %s.
                Season is %s.
                Supplier is %s with supplier code %s.
                Location is %s.
                Country of origin is %s.
                Currency is %s.

                """.formatted(
                        header.get("Concept"),
                        header.get("Department"),
                        header.get("Order Type"),
                        header.get("Order Date"),
                        header.get("Season"),
                        header.get("Supplier"),
                        header.get("Supplier Code"),
                        header.get("Location"),
                        header.get("COO"),
                        header.get("Currency")));
    }

    private void appendPurchaseIntentNotes(Map<String, Object> json, StringBuilder sb) {

        List<String> notes = (List<String>) json.get("notes");

        if (notes == null || notes.isEmpty()) {
            return;
        }

        sb.append("Purchase intent notes: ");

        notes.forEach(note -> sb.append(note).append(" "));

        sb.append("\n\n");
    }

    @SuppressWarnings("unchecked")
    private void appendPurchaseIntentItem(Map<String, Object> json, StringBuilder sb) {

        Map<String, Object> item = (Map<String, Object>) json.get("item");

        if (item == null || item.isEmpty()) {
            return;
        }

        String piNo = String.valueOf(item.get("piNo"));

        sb.append("""
                Purchase Intent Number %s has style number %s.
                The buying colour is %s and PO colour is %s.
                The item belongs to group %s, department %s, class %s and sub class %s.
                Product generic name is %s.
                Size range is %s and season is %s.
                Total quantity for Purchase Intent Number %s is %s.

                """.formatted(
                        piNo,
                        item.get("styleNo"),
                        item.get("buyingColour"),
                        item.get("poColor"),
                        item.get("group"),
                        item.get("dept"),
                        item.get("class"),
                        item.get("subClass"),
                        item.get("productGenericName"),
                        item.get("sizeRange"),
                        item.get("season"),
                        piNo,
                        item.get("total")));

        Map<String, Object> sizes = (Map<String, Object>) item.get("sizes");

        if (sizes != null && !sizes.isEmpty()) {

            sb.append("Size quantities for Purchase Intent Number ")
                    .append(piNo)
                    .append(": ");

            boolean first = true;

            for (Map.Entry<String, Object> entry : sizes.entrySet()) {

                if (!first) {
                    sb.append(", ");
                }

                sb.append(entry.getKey()).append(" = ").append(entry.getValue());

                first = false;
            }

            sb.append(".\n\n");
        }
    }

    @SuppressWarnings("unchecked")
    private void appendDeliveryDetails(Map<String, Object> json, StringBuilder sb) {

        Map<String, Object> delivery = (Map<String, Object>) json.get("delivery");

        if (delivery == null || delivery.isEmpty()) {
            return;
        }

        sb.append("""
                Delivery information:
                Place of delivery is %s.
                Booking date is %s.
                Delivery date is %s.
                Item made with %s.
                Fabric construction is %s.
                Currency is %s.
                Cost is %s.
                MRP is %s.
                Buyer comments are %s.

                """.formatted(
                        delivery.get("placeOfDelivery"),
                        delivery.get("bookingDate"),
                        delivery.get("deliveryDate"),
                        delivery.get("itemMadeWith"),
                        delivery.get("fabricConstruction"),
                        delivery.get("currency"),
                        delivery.get("cost"),
                        delivery.get("mrp"),
                        delivery.get("buyerComments")));
    }
}
