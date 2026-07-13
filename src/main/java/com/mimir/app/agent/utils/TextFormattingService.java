package com.mimir.app.agent.utils;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Service for text formatting and validation operations.
 * Provides common string manipulation methods.
 */
@Component
public class TextFormattingService {

    public boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    public boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public boolean isValidStringValue(Object value) {
        if (value == null) {
            return false;
        }
        
        String str = value.toString().trim();
        return isNotBlank(str) 
                && !str.equalsIgnoreCase("null") 
                && !str.startsWith("[");
    }

    public String joinWithComma(Collection<String> collection) {
        if (collection == null || collection.isEmpty()) {
            return "";
        }
        
        return collection.stream()
                .collect(Collectors.joining(", "));
    }

    public String toUpperCaseSafe(String str) {
        return str == null ? "" : str.toUpperCase();
    }

    public String normalize(String str) {
        return str == null ? "" : str.trim().toLowerCase();
    }
}