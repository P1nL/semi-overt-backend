package com.platform.content.controller;

import com.platform.content.service.ArticleService;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/v1/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @DeleteMapping("/{articleId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> deleteArticle(@PathVariable Long articleId) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.adminDeleteArticle(articleId, adminId));
    }
}
