package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Search response model shared with the public article search API.
 *
 * <p>This DTO remains in the content module for response-model compatibility,
 * but the active public search implementation lives in {@code search-service}.</p>
 */
@Data
@Builder
public class SearchResp {

    private String keyword;
    private List<ArticleCardResp> list;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Long pages;
}
