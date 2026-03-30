package com.platform.controller;

import com.platform.dto.resp.SearchResp;
import com.platform.service.SearchService;
import com.platform.util.Result;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开搜索接口。
 * 面向前端提供已发布文章搜索能力。
 */
@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 按关键字搜索文章投影。
     * 底层数据来自 Elasticsearch 中由事件驱动维护的索引文档。
     */
    @GetMapping("/articles")
    public Result<SearchResp> searchArticles(
            @RequestParam @NotBlank(message = "搜索关键词不能为空") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        return Result.ok(searchService.search(keyword, page, pageSize));
    }
}
