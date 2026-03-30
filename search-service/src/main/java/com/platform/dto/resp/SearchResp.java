package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * SearchResp 响应模型，封装对应场景返回的数据结构。
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
