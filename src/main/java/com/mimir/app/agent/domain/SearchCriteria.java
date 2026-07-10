package com.mimir.app.agent.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchCriteria {
    private String divName;
    private List<String> groupName;
    private List<String> deptName;
    private List<String> className;
    private String subName;
    private String sizeRange;
    private String season;
    private List<String> hit;
    private List<String> style;
    private String vendor;
    private List<String> lineItemStatus;
    private String sheetName;
    private String sourceFile;
    private String nextPageCursor;
    private String afterId;
    private String afterStyle;
    private Integer first;
}
