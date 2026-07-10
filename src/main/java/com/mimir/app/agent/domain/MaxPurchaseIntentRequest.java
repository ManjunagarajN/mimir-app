package com.mimir.app.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxPurchaseIntentRequest {
    private MaxPurchaseLineItem maxPurchaseLineItems;
    private IntentRequest.Identity identity;
    private IntentRequest.SourceSnapshot sourceSnapshot;
    private IntentRequest.WorkflowCommand workflowCommand;
}
