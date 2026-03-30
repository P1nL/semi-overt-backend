package com.platform.service;

import com.platform.common.dto.internal.ApplyReviewResultReq;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.dto.internal.UserProfileArticlesQueryReq;
import com.platform.common.dto.internal.UserProfileArticlesResp;
import com.platform.common.event.ReviewDecidedEvent;
import com.platform.dto.resp.ArticleDetailResp;
import com.platform.dto.resp.SubmitResp;

import java.util.Map;

/**
 * 文章业务接口，定义对外暴露的服务能力。
 */

public interface ArticleService {

    /**
     * 新建空草稿
     * 默认状态 DRAFT，标题为空
     *
     * @param userId 当前登录用户 ID
     * @return {"id": articleId, "status": "DRAFT"}
     */
    Map<String, Object> createArticle(Long userId);

    /**
     * 获取文章详情
     * 权限规则：
     *   - APPROVED 状态：任何人可访问（currentUserId 可为 null）
     *   - PENDING 状态：作者本人 + 管理员
     *   - 其余状态（DRAFT / RETURNED / REJECTED）：仅作者本人
     *   - 无权访问时返回 404，而非 403（避免资源枚举）
     *
     * @param articleId     文章 ID
     * @param currentUserId 当前用户 ID，未登录时为 null
     */
    ArticleDetailResp getArticleDetail(Long articleId, Long currentUserId);

    /**
     * 提交审核
     * 前置检查：
     *   1. 文章属于当前用户
     *   2. 状态必须为 DRAFT 或 RETURNED
     *   3. 标题非空
     *   4. 正文字数 >= 50（从 Redis 或 MySQL 读取最新内容）
     *   5. 30 分钟内未重复提交（按 article_id 限流）
     * 操作：flush Redis 内容到 MySQL，状态 → PENDING，更新 submitCount / lastSubmittedAt
     *
     * @param articleId 文章 ID
     * @param userId    当前用户 ID
     */
    SubmitResp submitForReview(Long articleId, Long userId);

    /**
     * 取消审核
     * 前置检查：
     *   1. 文章属于当前用户
     *   2. 状态必须为 PENDING
     * 操作：状态 PENDING → DRAFT，写入 review_logs(action=CANCEL)
     *
     * @param articleId 文章 ID
     * @param userId    当前用户 ID
     * @return {"status": "DRAFT"}
     */
    Map<String, Object> cancelReview(Long articleId, Long userId);

    /**
     * 删除文章（逻辑删除）
     * 前置检查：
     *   1. 文章属于当前用户
     *   2. 状态只允许 DRAFT / RETURNED / REJECTED
     * 操作：逻辑删除 MySQL 记录，同步清理 Redis 草稿 Key
     * review_logs 保留不删除
     *
     * @param articleId 文章 ID
     * @param userId    当前用户 ID
     */
    void deleteArticle(Long articleId, Long userId);

    /**
     * 管理员删除任意文章（包含已发布文章）。
     *
     * @param articleId 文章 ID
     * @param adminId   当前管理员 ID
     * @return {"ok": true}
     */
    Map<String, Object> adminDeleteArticle(Long articleId, Long adminId);

    /**
     * 提供给 review-service 的内部快照接口。
     */
    ArticleReviewSnapshotDto getReviewSnapshot(Long articleId);

    /**
     * 由 review-service 调用，统一在内容服务内落文章状态变更。
     */
    void applyReviewResult(Long articleId, ApplyReviewResultReq req);

    void applyReviewDecisionEvent(ReviewDecidedEvent event);

    UserProfileArticlesResp getUserProfileArticles(UserProfileArticlesQueryReq req);
}
