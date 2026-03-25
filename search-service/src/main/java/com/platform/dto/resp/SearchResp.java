package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 搜索结果响应（P2 占位）
 * 对应接口：GET /api/v1/search/articles
 *
 * 一期保留结构定义，后期接入全文检索时直接复用此 DTO。
 * list 中的卡片结构与首页/分类页完全一致（ArticleCardResp 复用）。
 */
@Data
@Builder
public class SearchResp {

    /** 搜索关键词（原样回传，便于前端高亮） */
    private String keyword;

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