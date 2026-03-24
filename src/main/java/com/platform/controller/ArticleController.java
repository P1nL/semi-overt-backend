package com.platform.controller;

import com.platform.dto.req.SaveDraftReq;
import com.platform.dto.resp.ArticleDetailResp;
import com.platform.dto.resp.DraftItemResp;
import com.platform.dto.resp.SaveDraftResp;
import com.platform.dto.resp.SubmitResp;
import com.platform.service.ArticleService;
import com.platform.service.DraftService;
import com.platform.util.Result;
import com.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章与草稿模块接口
 * 基础路径：/api/v1/articles
 *
 * 接口清单：
 *   POST   /api/v1/articles                            新建空草稿
 *   PUT    /api/v1/articles/{articleId}/draft          自动保存草稿
 *   GET    /api/v1/articles/drafts                     草稿箱列表
 *   GET    /api/v1/articles/{articleId}                文章详情
 *   POST   /api/v1/articles/{articleId}/submit         提交审核
 *   POST   /api/v1/articles/{articleId}/cancel-review  取消审核
 *   DELETE /api/v1/articles/{articleId}                删除文章
 *
 * 注意：Spring MVC 对字面量路径（/drafts）优先级高于路径变量（/{articleId}），
 *       因此 GET /articles/drafts 不会匹配 /{articleId}，不存在歧义。
 */
@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final DraftService draftService;

    /**
     * 新建空草稿
     * POST /api/v1/articles
     * 需要登录
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> createArticle() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.createArticle(userId));
    }

    /**
     * 自动保存草稿
     * PUT /api/v1/articles/{articleId}/draft
     * 需要登录，仅文章作者可调用
     * 编辑器防抖和 30 秒兜底保存均调用此接口
     */
    @PutMapping("/{articleId}/draft")
    @PreAuthorize("isAuthenticated()")
    public Result<SaveDraftResp> saveDraft(
            @PathVariable Long articleId,
            @RequestBody SaveDraftReq req) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(draftService.saveDraft(articleId, userId, req));
    }

    /**
     * 草稿箱列表
     * GET /api/v1/articles/drafts
     * 需要登录，返回 DRAFT + RETURNED 状态文章
     */
    @GetMapping("/drafts")
    @PreAuthorize("isAuthenticated()")
    public Result<List<DraftItemResp>> getDraftList() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(draftService.getDraftList(userId));
    }

    /**
     * 获取文章详情
     * GET /api/v1/articles/{articleId}
     * Auth 可选：已登录时传 userId，未登录时为 null
     * 权限由 Service 层判断，无权访问统一返回 404
     */
    @GetMapping("/{articleId}")
    public Result<ArticleDetailResp> getArticleDetail(@PathVariable Long articleId) {
        // 未登录时 SecurityUtils.getCurrentUserId() 返回 null，Service 层按匿名处理
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.getArticleDetail(articleId, currentUserId));
    }

    /**
     * 提交审核
     * POST /api/v1/articles/{articleId}/submit
     * 需要登录，仅文章作者可调用
     */
    @PostMapping("/{articleId}/submit")
    @PreAuthorize("isAuthenticated()")
    public Result<SubmitResp> submitForReview(@PathVariable Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.submitForReview(articleId, userId));
    }

    /**
     * 取消审核
     * POST /api/v1/articles/{articleId}/cancel-review
     * 需要登录，仅文章作者可调用
     */
    @PostMapping("/{articleId}/cancel-review")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> cancelReview(@PathVariable Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(articleService.cancelReview(articleId, userId));
    }

    /**
     * 删除文章（逻辑删除）
     * DELETE /api/v1/articles/{articleId}
     * 需要登录，仅文章作者可调用
     * 仅允许删除 DRAFT / RETURNED / REJECTED 状态文章
     */
    @DeleteMapping("/{articleId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteArticle(@PathVariable Long articleId) {
        Long userId = SecurityUtils.getCurrentUserId();
        articleService.deleteArticle(articleId, userId);
        return Result.ok();
    }
}