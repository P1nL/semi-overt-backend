package com.platform.content.controller.internal;

import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.content.service.ArticleService;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/articles")
@RequiredArgsConstructor
public class InternalArticleController {

    private final ArticleService articleService;

    @GetMapping("/review-pending")
    public Result<List<ArticleReviewSnapshotDto>> pendingReviewSnapshots(
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(articleService.getPendingReviewSnapshots(afterId, limit));
    }

    @GetMapping("/{id}/review-snapshot")
    public Result<ArticleReviewSnapshotDto> reviewSnapshot(@PathVariable Long id) {
        return Result.ok(articleService.getReviewSnapshot(id));
    }

    @PostMapping("/{id}/apply-review-result")
    public Result<ReviewDecisionResultDto> applyReviewResult(
            @PathVariable Long id,
            @RequestBody ApplyReviewResultReq req) {
        return Result.ok(articleService.applyReviewResult(id, req));
    }

    @GetMapping("/{id}/review-decisions/{decisionId}")
    public Result<ReviewDecisionResultDto> reviewDecisionResult(
            @PathVariable Long id,
            @PathVariable String decisionId) {
        return Result.ok(articleService.getReviewDecisionResult(id, decisionId));
    }

    @PostMapping("/profile-page")
    public Result<UserProfileArticlesResp> profilePage(@RequestBody UserProfileArticlesQueryReq req) {
        return Result.ok(articleService.getUserProfileArticles(req));
    }
}
