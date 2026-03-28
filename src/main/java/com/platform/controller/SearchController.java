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
 * Historical single-module search controller.
 *
 * <p>This file remains only as legacy residue under the root {@code src/} tree.
 * The active public search API is implemented in {@code search-service}.</p>
 */
@Deprecated
@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/articles")
    public Result<SearchResp> searchArticles(
            @RequestParam @NotBlank(message = "搜索关键词不能为空") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        return Result.ok(searchService.search(keyword, page, pageSize));
    }
}
