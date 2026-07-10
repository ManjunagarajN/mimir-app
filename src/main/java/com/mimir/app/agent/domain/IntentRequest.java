package com.mimir.app.agent.domain;

import lombok.Data;

@Data
public class IntentRequest {
    @Data
    public static class Identity {
        private String refId;
        private Long purchaseIntentNo;
        private Long purchaseOrderNo;
    }

    @Data
    public static class SourceSnapshot {
        private String rowHash;
        private String lastUpdatedAmendAt;
        private String lastUpdatedAmendBy;
        private Boolean isAmended;
    }

    @Data
    public static class WorkflowCommand {
        private String event;
        private String allocationRequestedBy;
        private String allocationRequestedAt;
        private Boolean isAllocationRequested;
    }
}
