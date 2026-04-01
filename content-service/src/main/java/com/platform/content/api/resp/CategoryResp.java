package com.platform.content.api.resp;

import com.platform.kernel.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分类列表响应。
 */
@Data
@Builder
public class CategoryResp {

    private DurationCategory category;
    private List<ArticleCardResp> list;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Long pages;
}
