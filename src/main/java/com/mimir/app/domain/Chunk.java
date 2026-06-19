package com.mimir.app.domain;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Chunk {
    private String text;
    private Map<String, Object> metadata;
    private float score;
}
