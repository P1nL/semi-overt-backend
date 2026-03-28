package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response payload for {@code GET /api/v1/search/articles}.
 *
 * <p>The card structure intentionally reuses {@link ArticleCardResp} so search,
 * home, and category pages can consume a consistent article list model.</p>
 */
@Data
@Builder
public class SearchResp {

    /** Echoes the keyword back to the client for search state display. */
    private String keyword;

    /** Current page of article cards. */
    private List<ArticleCardResp> list;

    /** Total hit count. */
    private Long total;

    /** Current page number, starting from 1. */
    private Integer page;

    /** Requested page size after normalization. */
    private Integer pageSize;

    /** Total page count. */
    private Long pages;
}
