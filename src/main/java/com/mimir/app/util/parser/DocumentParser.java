package com.mimir.app.util.parser;

import java.util.List;
import java.util.Map;

public interface DocumentParser {

    /**
     * Parse raw file bytes into a list of structured records.
     * Receives bytes so implementations can open multiple streams if needed.
     */
    List<Map<String, Object>> parse(byte[] fileBytes, String filename) throws Exception;

    /**
     * Convert structured records into retrieval-optimised plain text.
     */
    String generateRetrievalText(List<Map<String, Object>> records);

    /**
     * Returns true if this parser handles the given file extension.
     */
    boolean supports(String extension);
}
