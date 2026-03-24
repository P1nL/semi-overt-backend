package com.platform.controller;

import com.platform.service.ArticleService;
import com.platform.util.Result;
import com.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @DeleteMapping("/{articleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> deleteArticle(@PathVariable Long articleId) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.adminDeleteArticle(articleId, adminId));
    }
}
