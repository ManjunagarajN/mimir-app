package com.mimir.app.mapper;

import org.springframework.stereotype.Component;

import com.mimir.app.domain.QueryContext;
import com.mimir.app.request.ChatStreamRequest;

@Component
public class RequestMapper {

    public QueryContext toMap(ChatStreamRequest req) {
        QueryContext ctx = new QueryContext();
        ctx.setQuery(extractQuery(req));
        ctx.setModel(req.getModel());
        ctx.setChatId(req.getChatId());
        ctx.setChatMode(req.getChatMode());
        return ctx;
    }

    private String extractQuery(ChatStreamRequest req) {
        return req.getMessages().stream()
                .filter(m -> "user".equalsIgnoreCase(m.getRole()))
                .reduce((first, second) -> second)
                .map(ChatStreamRequest.Message::getContent)
                .orElseThrow();
    }
}
