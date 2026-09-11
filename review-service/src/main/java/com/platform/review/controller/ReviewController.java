package com.platform.review.controller;

import com.platform.kernel.api.PageResponse;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import com.platform.review.api.req.ReviewActionReq;
import com.platform.review.api.resp.ReviewActionResp;
import com.platform.review.api.resp.ReviewDecisionStatusResp;
import com.platform.review.api.resp.ReviewListItemResp;
import com.platform.review.api.resp.ReviewLogResp;
import com.platform.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/pending")
    public Result<PageResponse<ReviewListItemResp>> getPendingList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return Result.ok(reviewService.getPendingList(adminId, page, pageSize));
    }

    @PostMapping({"/{articleId}/decision", "/{articleId}/action"})
    public Result<ReviewActionResp> doReview(
            @PathVariable Long articleId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReviewActionReq req) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return Result.ok(reviewService.doReview(articleId, adminId, idempotencyKey, req));
    }

    @GetMapping("/{articleId}/decision-status")
    public Result<ReviewDecisionStatusResp> decisionStatus(
            @PathVariable Long articleId,
            @RequestParam String decisionId) {
        Long adminId = SecurityUtils.getCurrentUserId();
        return Result.ok(reviewService.getDecisionStatus(articleId, adminId, decisionId));
    }

    @GetMapping("/{articleId}/logs")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ReviewLogResp>> getReviewLogs(@PathVariable Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(reviewService.getReviewLogs(articleId, userId));
    }
}
