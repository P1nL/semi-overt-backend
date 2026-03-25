package com.platform.service;

import com.platform.dto.resp.SearchResp;

/**
 * 搜索服务接口（P2 占位）
 * 一期不实现，接口定义提前约定，方便后续扩展。
 */
public interface SearchService {

    /**
     * 按关键词搜索 APPROVED 文章（P2 占位）
     * 一期实现：返回"暂未开放"提示
     * 后期可替换为全文检索或 Elasticsearch 实现
     *
     * @param keyword  搜索关键词
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     */
    SearchResp search(String keyword, int page, int pageSize);
}