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
 * Public search API for approved articles.
 */
@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * Search article projections by keyword.
     *
     * <p>The backing data comes from Elasticsearch projections that are updated
     * by article status change events.</p>
     *
     * @param keyword search keyword
     * @param page page number, default 1
     * @param pageSize page size, default 10
     * @return paged search results
     */
    @GetMapping("/articles")
    public Result<SearchResp> searchArticles(
            @RequestParam @NotBlank(message = "搜索关键词不能为空") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        return Result.ok(searchService.search(keyword, page, pageSize));
    }
}
