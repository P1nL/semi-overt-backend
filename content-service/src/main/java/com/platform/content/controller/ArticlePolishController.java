package com.platform.content.controller;

import com.platform.content.api.req.ArticlePolishReq;
import com.platform.content.api.resp.ArticlePolishResp;
import com.platform.content.service.ArticlePolishService;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/articles/ai-polish")
@RequiredArgsConstructor
public class ArticlePolishController {
    private final ArticlePolishService service;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<ArticlePolishResp> polish(@Valid @RequestBody ArticlePolishReq request) {
        return Result.ok(service.polish(SecurityUtils.getCurrentUserId(), request));
    }
}
