package com.platform.service.impl;

import com.platform.dto.resp.SearchResp;
import com.platform.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 搜索服务占位实现（P2）
 *
 * 一期：接口已定义、路由已放行，但逻辑未实现，直接返回空结果 + 提示信息。
 * 后期替换策略：
 *   简单版 → 已有 ArticleMapper.searchByKeyword（LIKE 查询）
 *   进阶版 → 接入 Elasticsearch 全文检索
 * 替换时只需修改此 impl，接口和 Controller 无需改动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    @Override
    public SearchResp search(String keyword, int page, int pageSize) {
        // P2 占位：暂不实现，返回空结果
        // 如需直接报错可改为：throw BusinessException.badRequest("搜索功能暂未开放");
        log.debug("搜索功能暂未实现，keyword={}", keyword);

        return SearchResp.builder()
                .keyword(keyword)
                .list(Collections.emptyList())
                .total(0L)
                .page(page)
                .pageSize(pageSize)
                .pages(0L)
                .build();
    }
}