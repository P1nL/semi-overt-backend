package com.platform.search.controller;

import com.platform.kernel.util.Result;
import com.platform.search.api.resp.SearchResp;
import com.platform.search.api.resp.UserSearchResp;
import com.platform.search.service.SearchService;
import com.platform.search.service.UserSearchService;
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
    private final UserSearchService userSearchService;

    /** Root path is the current frontend contract; /articles remains a compatibility alias. */
    @GetMapping({"", "/articles"})
    public Result<SearchResp> searchArticles(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(searchService.search(keyword, page, pageSize > 0 ? pageSize : limit));
    }

    @GetMapping("/users")
    public Result<UserSearchResp> searchUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(userSearchService.searchUsers(keyword, page, pageSize));
    }
}
