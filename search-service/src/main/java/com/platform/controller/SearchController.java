package com.platform.controller;

import com.platform.dto.resp.SearchResp;
import com.platform.service.SearchService;
import com.platform.util.Result;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 搜索接口（P2 占位）
 */
@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 搜索文章（P2 占位）
     * GET /api/v1/search/articles?keyword=xxx&page=1&pageSize=10
     *
     * 公开接口，无需登录。
     * 一期返回空列表，后续实现全文检索后直接替换 Service 逻辑。
     *
     * @param keyword  搜索关键词，必填
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 10
     */
    @GetMapping("/articles")
    public Result<SearchResp> searchArticles(
            @RequestParam @NotBlank(message = "搜索关键词不能为空") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        return Result.ok(searchService.search(keyword, page, pageSize));
    }
}