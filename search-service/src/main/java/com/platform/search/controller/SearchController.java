package com.platform.search.controller;

import com.platform.search.api.resp.SearchResp;
import com.platform.kernel.util.Result;
import com.platform.search.service.SearchService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/articles")
    public Result<SearchResp> searchArticles(
            @RequestParam @NotBlank(message = "Keyword is required") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(searchService.search(keyword, page, pageSize));
    }
}
