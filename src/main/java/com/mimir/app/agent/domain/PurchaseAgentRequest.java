package com.mimir.app.agent.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAgentRequest {
    private String email;
    private String password;
    private String query;
    private String accessToken;
    private List<String> poColors;
    private List<Long> piNumbers;
    private SearchCriteria searchCriteria;
}
