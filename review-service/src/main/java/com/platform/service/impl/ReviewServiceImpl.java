package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.client.AuthInternalClient;
import com.platform.client.ContentInternalClient;
import com.platform.common.api.PageResponse;
import com.platform.common.api.ResultUtils;
import com.platform.common.constant.EventConstants;
import com.platform.common.context.TraceContextHolder;
import com.platform.common.dto.internal.ApplyReviewResultReq;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.dto.internal.BatchUserQueryReq;
import com.platform.common.dto.internal.ReviewDecisionPayload;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.common.event.ReviewDecidedEvent;
import com.platform.common.support.EventOutboxService;
import com.platform.dto.req.ReviewActionReq;
import com.platform.dto.resp.ReviewActionResp;
import com.platform.dto.resp.ReviewListItemResp;
import com.platform.dto.resp.ReviewLogResp;
import com.platform.entity.ReviewLog;
import com.platform.entity.ReviewTask;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import com.platform.exception.BusinessException;
import com.platform.mapper.ReviewLogMapper;
import com.platform.mapper.ReviewTaskMapper;
import com.platform.service.ReviewService;
import com.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 审核主流程服务实现。
 * 负责待审核列表查询、审核动作执行以及审核日志查看。
 * 该类是 review-service 的状态裁决入口，并通过 outbox 向内容域发送 REVIEW_DECIDED 事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final AuthInternalClient authInternalClient;
    private final ContentInternalClient contentInternalClient;
    private final EventOutboxService eventOutboxService;

    /**
     * 查询待审核列表。
     * 当前管理员本人提交的文章会被排除，避免自审。
     */
    @Override
    public PageResponse<ReviewListItemResp> getPendingList(Long currentAdminId, int page, int pageSize) {
        Page<ReviewTask> pageResult = reviewTaskMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getStatus, ArticleStatus.PENDING)
                        .ne(currentAdminId != null, ReviewTask::getAuthorId, currentAdminId)
                        .orderByDesc(ReviewTask::getSubmittedAt, ReviewTask::getArticleId)
        );

        List<ReviewTask> tasks = pageResult.getRecords();
        Map<Long, UserSummaryDto> userMap = batchFetchUsers(tasks.stream()
                .map(ReviewTask::getAuthorId)
                .collect(Collectors.toSet()));

        List<ReviewListItemResp> list = tasks.stream()
                .map(task -> {
                    UserSummaryDto author = userMap.get(task.getAuthorId());
                    return ReviewListItemResp.builder()
                            .id(task.getArticleId())
                            .title(task.getTitle())
                            .submitCount(task.getSubmitCount())
                            .submittedAt(task.getSubmittedAt())
                            .wordCount(task.getWordCount())
                            .author(author == null ? null : ReviewListItemResp.AuthorInfo.builder()
                                    .id(author.getId())
                                    .username(author.getUsername())
                                    .build())
                            .build();
                })
                .toList();

        return PageResponse.<ReviewListItemResp>builder()
                .list(list)
                .total(pageResult.getTotal())
                .page(pageResult.getCurrent())
                .pageSize(pageResult.getSize())
                .pages(pageResult.getPages())
                .build();
    }

    /**
     * 执行一次审核动作。
     * 会先拉取内容域快照确认文章仍处于 PENDING，再写审核日志、删除审核任务并发送 REVIEW_DECIDED 事件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewActionResp doReview(Long articleId, Long currentAdminId, ReviewActionReq req) {
        ReviewAction action = parseAction(req.getAction());
        validateActionRequest(action, req.getReason());

        ArticleReviewSnapshotDto snapshot = ResultUtils.requireOk(contentInternalClient.reviewSnapshot(articleId));
        if (snapshot == null) {
            throw BusinessException.notFound("Article not found");
        }
        if (snapshot.getStatus() != ArticleStatus.PENDING) {
            throw BusinessException.conflict(
                    "Article is no longer pending review: " + snapshot.getStatus());
        }
        if (currentAdminId != null && currentAdminId.equals(snapshot.getAuthorId())) {
            throw BusinessException.forbidden("Administrators cannot review their own article");
        }

        ArticleStatus toStatus = toDecisionStatus(action);
        LocalDateTime reviewedAt = LocalDateTime.now();
        ReviewDecisionPayload decision = ReviewDecisionPayload.builder()
                .articleId(articleId)
                .adminId(currentAdminId)
                .action(action)
                .reason(normalizeReason(req.getReason()))
                .reviewedAt(reviewedAt)
                .fromStatus(snapshot.getStatus())
                .toStatus(toStatus)
                .traceId(TraceContextHolder.get())
                .build();

        reviewLogMapper.insert(toReviewLog(decision));
        reviewTaskMapper.delete(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getArticleId, articleId));
        eventOutboxService.saveEvent(
                "review",
                String.valueOf(articleId),
                EventConstants.REVIEW_DECIDED,
                ReviewDecidedEvent.fromPayload(newEventId(articleId), decision)
        );

        log.info("Review decided: articleId={}, adminId={}, action={}, toStatus={}, traceId={}",
                articleId, currentAdminId, action, toStatus, decision.getTraceId());

        return ReviewActionResp.builder()
                .status(toStatus)
                .reviewedAt(reviewedAt)
                .build();
    }

    /**
     * 查询文章审核日志。
     * 仅管理员或文章作者本人可以查看。
     */
    @Override
    public List<ReviewLogResp> getReviewLogs(Long articleId, Long currentUserId) {
        ArticleReviewSnapshotDto snapshot = ResultUtils.requireOk(contentInternalClient.reviewSnapshot(articleId));
        if (snapshot == null) {
            throw BusinessException.notFound("Article not found");
        }

        boolean isAdmin = SecurityUtils.isAdmin();
        boolean isAuthor = currentUserId != null && currentUserId.equals(snapshot.getAuthorId());
        if (!isAdmin && !isAuthor) {
            throw BusinessException.forbidden("Access denied");
        }

        List<ReviewLog> logs = reviewLogMapper.selectList(new LambdaQueryWrapper<ReviewLog>()
                .eq(ReviewLog::getArticleId, articleId)
                .orderByAsc(ReviewLog::getCreatedAt));
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, UserSummaryDto> userMap = batchFetchUsers(logs.stream()
                .map(ReviewLog::getOperatorId)
                .collect(Collectors.toSet()));

        return logs.stream()
                .map(log -> {
                    UserSummaryDto operator = userMap.get(log.getOperatorId());
                    return ReviewLogResp.builder()
                            .action(log.getAction())
                            .fromStatus(log.getFromStatus())
                            .toStatus(log.getToStatus())
                            .reason(log.getReason())
                            .operator(operator == null ? null : ReviewLogResp.OperatorInfo.builder()
                                    .id(operator.getId())
                                    .username(operator.getUsername())
                                    .build())
                            .createdAt(log.getCreatedAt())
                            .build();
                })
                .toList();
    }

    /**
     * 将外部请求中的动作字符串转换为审核动作枚举。
     */
    private ReviewAction parseAction(String actionValue) {
        try {
            return ReviewAction.valueOf(actionValue.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Unsupported review action: " + actionValue);
        }
    }

    /**
     * 校验审核动作的业务约束。
     * CANCEL 只允许走作者撤回链路；RETURN 和 REJECT 必须提供原因。
     */
    private void validateActionRequest(ReviewAction action, String reason) {
        if (action == ReviewAction.CANCEL) {
            throw BusinessException.badRequest("CANCEL is owned by the article cancel-review flow");
        }
        if ((action == ReviewAction.RETURN || action == ReviewAction.REJECT)
                && (reason == null || reason.isBlank())) {
            throw BusinessException.badRequest("Reason is required for RETURN and REJECT");
        }
    }

    /**
     * 把审核动作映射为文章目标状态。
     */
    private ArticleStatus toDecisionStatus(ReviewAction action) {
        return switch (action) {
            case APPROVE -> ArticleStatus.APPROVED;
            case RETURN -> ArticleStatus.RETURNED;
            case REJECT -> ArticleStatus.REJECTED;
            default -> throw BusinessException.badRequest("Unsupported review action");
        };
    }

    /**
     * 将审核决策载荷转换为 review_logs 记录。
     */
    private ReviewLog toReviewLog(ReviewDecisionPayload decision) {
        ReviewLog reviewLog = new ReviewLog();
        reviewLog.setArticleId(decision.getArticleId());
        reviewLog.setOperatorId(decision.getAdminId());
        reviewLog.setAction(decision.getAction());
        reviewLog.setFromStatus(decision.getFromStatus());
        reviewLog.setToStatus(decision.getToStatus());
        reviewLog.setReason(decision.getReason());
        return reviewLog;
    }

    /**
     * 标准化审核原因文本。
     * 空串会被折叠为 null，避免写入无意义空白内容。
     */
    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 批量查询用户摘要信息，避免审核列表和日志出现 N+1 调用。
     */
    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return ResultUtils.requireOk(authInternalClient.batchUsers(new BatchUserQueryReq(ids.stream().toList())))
                .stream()
                .collect(Collectors.toMap(UserSummaryDto::getId, user -> user));
    }

    /**
     * 构造审核决策事件 ID，便于跨服务追踪。
     */
    private String newEventId(Long articleId) {
        return "review-decided:" + articleId + ":" + UUID.randomUUID();
    }
}
