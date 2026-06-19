package com.mimir.app.domain;

import java.util.UUID;

import lombok.Data;

@Data
public class QueryContext {
    private String query;
    private String model;
    private String chatMode;
    private UUID chatId;
    private String region;
    private String accessLevel;
}
