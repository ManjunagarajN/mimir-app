package com.mimir.app.agent.utils;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for collection operations.
 * Provides common collection manipulation methods.
 */
@Component
public class DataCollectionService {

    public boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    public <T> List<T> distinct(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        
        return collection.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    @SafeVarargs
    public final <T> List<T> immutableList(T... elements) {
        return List.of(elements);
    }

    public Map<String, Object> immutableMap(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide even number of arguments");
        }
        
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i].toString(), pairs[i + 1]);
        }
        
        return Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    public <T> T getValueOrDefault(Map<String, Object> map, String key, T defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = map.get(key);
        return value != null ? (T) value : defaultValue;
    }
}