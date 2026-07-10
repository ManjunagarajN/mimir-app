package com.mimir.app.agent.memory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionMemory {
    private String sessionId;
    private String lastCity;
    private Map<String, Object> context;
    private LocalDateTime lastAccess;
    private int totalQueries;

    public SessionMemory(String sessionId) {
        this.sessionId = sessionId;
        this.context = new HashMap<>();
        this.lastAccess = LocalDateTime.now();
        this.totalQueries = 0;
    }
}
