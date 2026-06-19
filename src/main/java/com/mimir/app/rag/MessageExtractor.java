package com.mimir.app.rag;

import com.mimir.app.request.ChatStreamRequest;

public class MessageExtractor {

    public static String extract(ChatStreamRequest request) {

        if (request.getMessages() == null) return "";

        return request.getMessages().stream()
                .filter(m -> m.getContent() != null)
                .map(ChatStreamRequest.Message::getContent)
                .reduce("", (a, b) -> a + "\n" + b)
                .trim();
    }
}
