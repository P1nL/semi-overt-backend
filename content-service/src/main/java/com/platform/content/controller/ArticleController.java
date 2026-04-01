package com.platform.content.controller;

import com.platform.content.api.req.SaveDraftReq;
import com.platform.content.api.resp.ArticleDetailResp;
import com.platform.content.api.resp.DraftItemResp;
import com.platform.content.api.resp.SaveDraftResp;
import com.platform.content.api.resp.SubmitResp;
import com.platform.content.service.ArticleService;
import com.platform.content.service.DraftService;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final DraftService draftService;

        @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> createArticle() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.createArticle(userId));
    }

        @PutMapping("/{articleId}/draft")
    @PreAuthorize("isAuthenticated()")
    public Result<SaveDraftResp> saveDraft(
            @PathVariable Long articleId,
            @RequestBody SaveDraftReq req) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(draftService.saveDraft(articleId, userId, req));
    }

        @GetMapping("/drafts")
    @PreAuthorize("isAuthenticated()")
    public Result<List<DraftItemResp>> getDraftList() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(draftService.getDraftList(userId));
    }

        @GetMapping("/{articleId}")
    public Result<ArticleDetailResp> getArticleDetail(@PathVariable Long articleId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.getArticleDetail(articleId, currentUserId));
    }

        @PostMapping("/{articleId}/submit")
    @PreAuthorize("isAuthenticated()")
    public Result<SubmitResp> submitForReview(@PathVariable Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.submitForReview(articleId, userId));
    }

        @PostMapping("/{articleId}/cancel-review")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> cancelReview(@PathVariable Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.cancelReview(articleId, userId));
    }

        @DeleteMapping("/{articleId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteArticle(@PathVariable Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        articleService.deleteArticle(articleId, userId);
        return Result.ok();
    }
}


