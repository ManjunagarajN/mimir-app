package com.mimir.app.agent.domain;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

@Builder
public record LineItemsFilterRequest(
        @NotBlank(message = "Please provide a division name.")
        String divName,

        @NotEmpty(message = "Please select at least one group.")
        List<@NotBlank(message = "Each group name must be valid.") String> groupName,

        @NotEmpty(message = "Please select at least one department.")
        List<@NotBlank(message = "Each department name must be valid.") String> deptName,

        List<String> className,
        String subName,
        String sizeRange,
        String season,
        List<String> hit,
        List<String> style,
        String vendor,
        List<String> lineItemStatus,
        String sheetName,
        String sourceFile,
        String nextPageCursor,
        String afterId,
        String afterStyle,
        Integer first) {
    public int getFirst() {
        if (first == null || first <= 0) return 50;
        return Math.min(first, 1000);
    }
}
