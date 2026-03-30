package com.platform.controller;

import com.platform.common.api.PageResponse;
import com.platform.dto.req.ReviewActionReq;
import com.platform.dto.resp.ReviewActionResp;
import com.platform.dto.resp.ReviewListItemResp;
import com.platform.dto.resp.ReviewLogResp;
import com.platform.service.ReviewService;
import com.platform.util.Result;
import com.platform.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核控制器，对外提供相关 HTTP 接口。
 */

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 获取待审核文章列表
     * GET /api/v1/reviews/pending
     * 仅管理员（SecurityConfig 已全局拦截）
     */
    @GetMapping("/pending")
    public Result<PageResponse<ReviewListItemResp>> getPendingList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        Long adminId = SecurityUtils.getCurrentUserId();
        return Result.ok(reviewService.getPendingList(adminId, page, pageSize));
    }

    /**
     * 提交审核动作（通过 / 退回 / 拒绝）
     * POST /api/v1/reviews/{articleId}/action
     * 仅管理员
     */
    @PostMapping("/{articleId}/action")
    public Result<ReviewActionResp> doReview(
            @PathVariable Long articleId,
            @Valid @RequestBody ReviewActionReq req) {

        Long adminId = SecurityUtils.getCurrentUserId();
        return Result.ok(reviewService.doReview(articleId, adminId, req));
    }

    /**
     * 获取文章审核日志
     * GET /api/v1/reviews/{articleId}/logs
     *
     * 权限：
     *   - 管理员：可查任意文章的审核日志
     *   - 文章作者：只能查自己文章的日志（Service 层校验，此处放行已登录用户）
     *
     * 注意：SecurityConfig 把 /api/v1/reviews/** 全部限制为 ADMIN，
     * 但作者也需要访问自己的审核日志（编辑器顶部原因提示）。
     * 这里通过 @PreAuthorize 覆盖为：已登录用户皆可调用，Service 层再做权限细分。
     */
    @GetMapping("/{articleId}/logs")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ReviewLogResp>> getReviewLogs(@PathVariable Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(reviewService.getReviewLogs(articleId, userId));
    }
}
