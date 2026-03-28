package com.platform.service;

import com.platform.common.api.PageResponse;
import com.platform.dto.req.ReviewActionReq;
import com.platform.dto.resp.ReviewActionResp;
import com.platform.dto.resp.ReviewListItemResp;
import com.platform.dto.resp.ReviewLogResp;

import java.util.List;

/**
 * 审核服务接口
 * 覆盖：待审核列表 / 审核动作 / 审核日志
 */
public interface ReviewService {

    /**
     * 获取待审核文章列表（仅 ADMIN 可调用）
     * 规则：
     *   - 仅返回 status=PENDING 的文章
     *   - 排除当前管理员自己提交的文章（需由其他管理员审核）
     *   - 按提交时间倒序排列
     *
     * @param currentAdminId 当前管理员 ID
     * @param page           页码（从 1 开始）
     * @param pageSize       每页条数
     * @return 分页结果（包含列表 + 总数）
     */
    PageResponse<ReviewListItemResp> getPendingList(Long currentAdminId, int page, int pageSize);

    /**
     * 提交审核动作（仅 ADMIN 可调用）
     * 规则：
     *   1. 文章必须为 PENDING 状态（并发审核保护）
     *   2. 管理员不能审核自己提交的文章
     *   3. RETURN / REJECT 时 reason 必填
     *   4. 写入 review_logs，更新 articles.status
     *   5. APPROVE 时同步写入 published_at
     *
     * @param articleId      文章 ID
     * @param currentAdminId 当前管理员 ID
     * @param req            审核动作请求
     */
    ReviewActionResp doReview(Long articleId, Long currentAdminId, ReviewActionReq req);

    /**
     * 获取文章审核日志
     * 权限规则：
     *   - 管理员：可查看任意文章的审核日志
     *   - 普通用户：只能查看自己文章的日志
     *   - 无权限时抛 403
     *
     * @param articleId     文章 ID
     * @param currentUserId 当前用户 ID
     */
    List<ReviewLogResp> getReviewLogs(Long articleId, Long currentUserId);
}
