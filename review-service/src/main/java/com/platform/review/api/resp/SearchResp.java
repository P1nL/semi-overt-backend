package com.platform.review.api.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;


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



