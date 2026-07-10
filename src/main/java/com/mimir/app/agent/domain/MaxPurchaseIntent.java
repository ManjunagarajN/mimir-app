package com.mimir.app.agent.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxPurchaseIntent {
    private Long id;

    private String refId;
    private String cdcHash;
    private String supplier;
    private String style;
    private String groupName;
    private String department;
    private String division;
    private String className;
    private String subClass;
    private String season;
    private String productGenericName;
    private String poColour;
    private String buyingColour;
    private String finalTotal;
    private String hit;
    private String sizeRange;
    private String placeOfDelivery;
    private String location;
    private String bookingDate;
    private String deliveryDate;
    private String fabricComposition;
    private String fabricDescription;
    private java.math.BigDecimal cost;
    private java.math.BigDecimal mrp;
    private String itemDescription;
    private String finalCostPrice;
    private String dutyFactor;
    private String currencyConversion;
    private String currency;
    private String purchaseType;
    private String supplierCode;
    private String orderType;
    private String countryOfOrigin;
    private String importDomestic;
    private String status;
    private String buyerComments;
    private boolean isAllocationRequested;
    private LocalDateTime allocationRequestedAt;
    private LocalDateTime allocationModifiedAt;
    private boolean isAmended;
    private LocalDateTime amendedRequestedAt;
    private LocalDateTime amendedModifiedAt;
}
