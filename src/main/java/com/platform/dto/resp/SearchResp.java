package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Historical single-module search response model.
 *
 * <p>This root {@code src/} tree is legacy residue from the pre-microservice
 * layout and is not part of the active parent-module build. The live public
 * search API is implemented in {@code search-service}.</p>
 */
@Deprecated
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
