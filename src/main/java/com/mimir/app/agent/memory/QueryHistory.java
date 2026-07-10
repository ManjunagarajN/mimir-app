package com.mimir.app.agent.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryHistory {
    private String sessionId;
    private List<String> queries;
    private List<String> actions;
    private LocalDateTime lastUpdate;

    public QueryHistory(String sessionId) {
        this.sessionId = sessionId;
        this.queries = new ArrayList<>();
        this.actions = new ArrayList<>();
        this.lastUpdate = LocalDateTime.now();
    }
}
