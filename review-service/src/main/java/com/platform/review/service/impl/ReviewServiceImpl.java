package com.platform.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.contract.content.client.ContentReviewClient;
import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.kernel.api.PageResponse;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.exception.BusinessException;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import com.platform.review.api.req.ReviewActionReq;
import com.platform.review.api.resp.ReviewActionResp;
import com.platform.review.api.resp.ReviewDecisionStatusResp;
import com.platform.review.api.resp.ReviewListItemResp;
import com.platform.review.api.resp.ReviewLogResp;
import com.platform.review.entity.ReviewCommand;
import com.platform.review.entity.ReviewLog;
import com.platform.review.entity.ReviewTask;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.ReviewDecisionCoordinator;
import com.platform.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final AuthUserQueryClient authUserQueryClient;
    private final ContentReviewClient contentReviewClient;
    private final ReviewDecisionCoordinator decisionCoordinator;

    @Override
    public PageResponse<ReviewListItemResp> getPendingList(Long currentAdminId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        Page<ReviewTask> pageResult = reviewTaskMapper.selectPage(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getStatus, ArticleStatus.PENDING)
                        .eq(ReviewTask::getAssignedAdminId, currentAdminId)
                        .orderByAsc(ReviewTask::getSubmittedAt, ReviewTask::getArticleId));
        Map<Long, UserSummaryDto> users = batchFetchUsers(pageResult.getRecords().stream()
                .map(ReviewTask::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<ReviewListItemResp> items = pageResult.getRecords().stream().map(task -> {
            UserSummaryDto author = users.get(task.getAuthorId());
            return ReviewListItemResp.builder()
                    .id(task.getArticleId())
                    .title(task.getTitle())
                    .submitCount(task.getSubmitCount())
                    .submittedAt(task.getSubmittedAt())
                    .wordCount(task.getWordCount())
                    .author(author == null ? null : ReviewListItemResp.AuthorInfo.builder()
                            .id(author.getId()).username(author.getUsername()).build())
                    .build();
        }).toList();
        return PageResponse.<ReviewListItemResp>builder()
                .list(items)
                .total(pageResult.getTotal())
                .page(pageResult.getCurrent())
                .pageSize(pageResult.getSize())
                .pages(pageResult.getPages())
                .build();
    }

    @Override
    public ReviewActionResp doReview(Long articleId, Long currentAdminId,
                                     String idempotencyKey, ReviewActionReq req) {
        if (req == null) throw BusinessException.badRequest("Review request is required");
        String key = selectDecisionId(idempotencyKey, req.getDecisionId());
        ReviewCommand command = decisionCoordinator.claim(
                articleId, currentAdminId, key, req.getAction(), req.getReason(),
                req.getSubmissionId(), req.getExpectedVersion());
        if (ReviewDecisionCoordinatorImpl.FINAL.equals(command.getState())) {
            return actionResponse(command);
        }
        if (ReviewDecisionCoordinatorImpl.CONFLICT.equals(command.getState())) {
            throw decisionConflict(command);
        }

        Result<ReviewDecisionResultDto> remote;
        try {
            remote = contentReviewClient.applyReviewResult(articleId, new ApplyReviewResultReq(
                    command.getAdminId(), command.getAction(), command.getReason(),
                    command.getDecisionId(), command.getSubmissionId(), command.getExpectedVersion()));
        } catch (Exception unavailableOrUnknown) {
            throw processingUnavailable(command.getDecisionId());
        }
        if (remote == null || remote.getCode() == null || remote.getData() == null
                || (remote.getCode() != 200 && remote.getCode() != 409)) {
            throw processingUnavailable(command.getDecisionId());
        }
        ReviewCommand finalized = decisionCoordinator.finalizeAuthoritativeResult(remote.getData());
        if (ReviewDecisionCoordinatorImpl.CONFLICT.equals(finalized.getState())) {
            throw decisionConflict(finalized);
        }
        return actionResponse(finalized);
    }

    @Override
    public ReviewDecisionStatusResp getDecisionStatus(Long articleId, Long currentAdminId, String decisionId) {
        ReviewCommand command = decisionCoordinator.find(decisionId);
        if (command == null || !Objects.equals(command.getArticleId(), articleId)) {
            throw BusinessException.notFound("Review decision not found; retry POST with the same decisionId");
        }
        if (!Objects.equals(command.getAdminId(), currentAdminId)) {
            throw BusinessException.forbidden("Only the assigned administrator may query this decision");
        }
        return ReviewDecisionStatusResp.builder()
                .decisionId(command.getDecisionId())
                .state(command.getState())
                .status(command.getStatus())
                .updatedAt(command.getUpdatedAt())
                .build();
    }

    @Override
    public List<ReviewLogResp> getReviewLogs(Long articleId, Long currentUserId) {
        ArticleReviewSnapshotDto snapshot;
        try {
            Result<ArticleReviewSnapshotDto> result = contentReviewClient.reviewSnapshot(articleId);
            if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
                throw BusinessException.notFound("Article not found");
            }
            snapshot = result.getData();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(503, "Content authority is temporarily unavailable");
        }
        boolean isAuthor = Objects.equals(currentUserId, snapshot.getAuthorId());
        if (!SecurityUtils.isAdmin() && !isAuthor) {
            throw BusinessException.forbidden("Access denied");
        }
        List<ReviewLog> logs = reviewLogMapper.selectList(new LambdaQueryWrapper<ReviewLog>()
                .eq(ReviewLog::getArticleId, articleId)
                .orderByAsc(ReviewLog::getCreatedAt, ReviewLog::getId));
        if (logs.isEmpty()) return Collections.emptyList();
        Map<Long, UserSummaryDto> users = batchFetchUsers(logs.stream()
                .map(ReviewLog::getOperatorId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return logs.stream().map(log -> {
            UserSummaryDto operator = users.get(log.getOperatorId());
            return ReviewLogResp.builder()
                    .action(log.getAction())
                    .fromStatus(log.getFromStatus())
                    .toStatus(log.getToStatus())
                    .reason(log.getReason())
                    .operator(operator == null ? null : ReviewLogResp.OperatorInfo.builder()
                            .id(operator.getId()).username(operator.getUsername()).build())
                    .createdAt(log.getCreatedAt())
                    .build();
        }).toList();
    }

    private String selectDecisionId(String headerKey, String bodyKey) {
        String header = normalizeKey(headerKey);
        String body = normalizeKey(bodyKey);
        if (header != null && body != null && !header.equals(body)) {
            throw BusinessException.conflict("Idempotency-Key and request decisionId must match");
        }
        return header != null ? header : body;
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim();
        if (normalized.length() > 64) {
            throw BusinessException.badRequest("decisionId must not exceed 64 characters");
        }
        return normalized;
    }

    private ReviewActionResp actionResponse(ReviewCommand command) {
        return ReviewActionResp.builder()
                .decisionId(command.getDecisionId())
                .state(command.getState())
                .status(command.getStatus())
                .reviewedAt(command.getUpdatedAt())
                .build();
    }

    private BusinessException processingUnavailable(String decisionId) {
        return new BusinessException(503,
                "Review result is unknown; query status or retry with the same decisionId",
                Map.of("decisionId", decisionId, "state", ReviewDecisionCoordinatorImpl.PROCESSING));
    }

    private BusinessException decisionConflict(ReviewCommand command) {
        return new BusinessException(409,
                "Content authority rejected the review decision",
                Map.of("decisionId", command.getDecisionId(), "state", ReviewDecisionCoordinatorImpl.CONFLICT));
    }

    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        Result<List<UserSummaryDto>> result = authUserQueryClient.batchUsers(
                new BatchUserQueryReq(ids.stream().sorted().toList()));
        if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
            return Collections.emptyMap();
        }
        return result.getData().stream().filter(user -> user.getId() != null)
                .collect(Collectors.toMap(UserSummaryDto::getId, user -> user, (left, right) -> left));
    }
}

