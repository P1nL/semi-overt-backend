package com.platform.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.content.client.ContentReviewClient;
import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.api.PageResponse;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.context.TraceContextHolder;
import com.platform.kernel.event.ReviewDecisionPayload;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.events.support.EventOutboxService;
import com.platform.review.api.req.ReviewActionReq;
import com.platform.review.api.resp.ReviewActionResp;
import com.platform.review.api.resp.ReviewListItemResp;
import com.platform.review.api.resp.ReviewLogResp;
import com.platform.review.entity.ReviewLog;
import com.platform.review.entity.ReviewTask;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.exception.BusinessException;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.ReviewService;
import com.platform.kernel.util.SecurityUtils;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final AuthUserQueryClient authInternalClient;
    private final ContentReviewClient contentInternalClient;
    private final EventOutboxService eventOutboxService;

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

    private void validateActionRequest(ReviewAction action, String reason) {
        if (action == ReviewAction.CANCEL) {
            throw BusinessException.badRequest("CANCEL is owned by the article cancel-review flow");
        }
        if ((action == ReviewAction.RETURN || action == ReviewAction.REJECT)
                && (reason == null || reason.isBlank())) {
            throw BusinessException.badRequest("Reason is required for RETURN and REJECT");
        }
    }

    private ReviewAction parseAction(String actionValue) {
        if (actionValue == null || actionValue.isBlank()) {
            throw BusinessException.badRequest("Review action is required");
        }
        try {
            return ReviewAction.valueOf(actionValue.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Unsupported review action: " + actionValue);
        }
    }

        private ArticleStatus toDecisionStatus(ReviewAction action) {
        return switch (action) {
            case APPROVE -> ArticleStatus.APPROVED;
            case RETURN -> ArticleStatus.RETURNED;
            case REJECT -> ArticleStatus.REJECTED;
            default -> throw BusinessException.badRequest("Unsupported review action");
        };
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

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

        private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return ResultUtils.requireOk(authInternalClient.batchUsers(new BatchUserQueryReq(ids.stream().toList())))
                .stream()
                .collect(Collectors.toMap(UserSummaryDto::getId, user -> user));
    }

        private String newEventId(Long articleId) {
        return "review-decided:" + articleId + ":" + UUID.randomUUID();
    }
}




