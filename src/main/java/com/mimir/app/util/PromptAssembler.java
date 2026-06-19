package com.mimir.app.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PromptAssembler {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String assemble(String templateFile, Map<String, String> variables) {
        String result = this.cache.computeIfAbsent(templateFile, key -> {
            try {
                ClassPathResource resource = new ClassPathResource("prompts/" + key);
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load prompt: " + key, e);
            }
        });
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = (entry.getValue() != null) ? entry.getValue() : "";
            result = result.replace("{{" + (String) entry.getKey() + "}}", value);
        }
        return result;
    }
}
