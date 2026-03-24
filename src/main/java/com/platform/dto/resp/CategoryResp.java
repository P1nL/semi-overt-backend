package com.platform.dto.resp;

import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分类详情列表响应
 * 对应接口：GET /api/v1/categories/{category}/articles
 *
 * 包含分页元数据，前端可据此渲染"加载更多"或分页导航。
 */
@Data
@Builder
public class CategoryResp {

    /** 阅读时长分类 */
    private DurationCategory category;

    /** 当前页文章卡片列表 */
    private List<ArticleCardResp> list;

    /** 总记录数 */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Integer page;

    /** 每页条数 */
    private Integer pageSize;

    /** 总页数 */
    private Long pages;
}