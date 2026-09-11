package com.platform.review.service.impl;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.review.dto.ReviewAssignmentDto;
import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.exception.BusinessException;
import com.platform.review.api.resp.ReviewReconciliationResp;
import com.platform.review.entity.ReviewTask;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.ReviewTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ReviewTaskServiceImpl implements ReviewTaskService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final AuthUserQueryClient authUserQueryClient;
    private final TransactionTemplate transactions;

    public ReviewTaskServiceImpl(ReviewTaskMapper reviewTaskMapper,
                                 AuthUserQueryClient authUserQueryClient,
                                 PlatformTransactionManager transactionManager) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.authUserQueryClient = authUserQueryClient;
        // REQUIRED joins EventListenerExecutor's inbox transaction.
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void upsertTask(ReviewTaskUpsertReq req) {
        requireVersioned(req == null ? null : req.getArticleId(),
                req == null ? null : req.getSubmissionId(),
                req == null ? null : req.getArticleVersion());
        Long assignment = req.getStatus() == ArticleStatus.PENDING
                ? selectAssignee(req.getAuthorId(), req.getSubmissionId()) : null;
        transactions.executeWithoutResult(ignored ->
                logProjection(req, applyProjection(req, assignment)));
    }

    @Override
    public void removeTask(ReviewTaskRemoveReq req) {
        requireVersioned(req == null ? null : req.getArticleId(),
                req == null ? null : req.getSubmissionId(),
                req == null ? null : req.getArticleVersion());
        ReviewTaskUpsertReq tombstone = ReviewTaskUpsertReq.builder()
                .articleId(req.getArticleId())
                .submissionId(req.getSubmissionId())
                .articleVersion(req.getArticleVersion())
                .status(ArticleStatus.DRAFT)
                .lastEventId(req.getLastEventId())
                .build();
        transactions.executeWithoutResult(ignored ->
                logProjection(tombstone, applyProjection(tombstone, null)));
    }

    @Override
    public void projectStatus(ArticleStatusChangedEvent event) {
        if (event == null || event.getArticleId() == null || event.getArticleVersion() == null
                || event.getArticleVersion() < 0) {
            throw BusinessException.badRequest("Versioned status projection requires articleId and articleVersion");
        }
        boolean terminalWithoutSubmission = event.getToStatus() != ArticleStatus.PENDING
                && (event.getSubmissionId() == null || event.getSubmissionId().isBlank());
        if (terminalWithoutSubmission) {
            log.info("Ignore never-submitted terminal article event: articleId={}, version={}, eventId={}",
                    event.getArticleId(), event.getArticleVersion(), event.getEventId());
            return;
        }
        requireVersioned(event.getArticleId(), event.getSubmissionId(), event.getArticleVersion());
        Long assignment = event.getToStatus() == ArticleStatus.PENDING && !Boolean.TRUE.equals(event.getDeleted())
                ? selectAssignee(event.getAuthorId(), event.getSubmissionId()) : null;
        ReviewTaskUpsertReq req = ReviewTaskUpsertReq.builder()
                .articleId(event.getArticleId())
                .authorId(event.getAuthorId())
                .title(event.getTitle())
                .status(Boolean.TRUE.equals(event.getDeleted()) ? ArticleStatus.DRAFT : event.getToStatus())
                .submissionId(event.getSubmissionId())
                .articleVersion(event.getArticleVersion())
                .lastEventId(event.getEventId())
                .build();
        transactions.executeWithoutResult(ignored ->
                logProjection(req, applyProjection(req, assignment)));
    }

    @Override
    public ReviewAssignmentDto assignment(Long articleId, String submissionId) {
        if (articleId == null || submissionId == null || submissionId.isBlank()) {
            throw BusinessException.badRequest("articleId and submissionId are required");
        }
        ReviewTask task = reviewTaskMapper.selectByArticleId(articleId);
        if (task == null || !submissionId.equals(task.getSubmissionId()) || task.getStatus() != ArticleStatus.PENDING) {
            throw BusinessException.notFound("Exact review assignment not found");
        }
        return ReviewAssignmentDto.builder()
                .articleId(articleId)
                .submissionId(submissionId)
                .assignedAdminId(task.getAssignedAdminId())
                .build();
    }

    @Override
    public ReviewReconciliationResp.Item repairFromSnapshot(ArticleReviewSnapshotDto snapshot, String source) {
        requireVersioned(snapshot == null ? null : snapshot.getArticleId(),
                snapshot == null ? null : snapshot.getSubmissionId(),
                snapshot == null ? null : snapshot.getVersion());
        ArticleStatus status = Boolean.TRUE.equals(snapshot.getDeleted()) ? ArticleStatus.DRAFT : snapshot.getStatus();
        Long assignment = status == ArticleStatus.PENDING
                ? selectAssignee(snapshot.getAuthorId(), snapshot.getSubmissionId()) : null;
        ReviewTaskUpsertReq req = ReviewTaskUpsertReq.builder()
                .articleId(snapshot.getArticleId())
                .authorId(snapshot.getAuthorId())
                .title(snapshot.getTitle())
                .wordCount(snapshot.getWordCount())
                .status(status)
                .submitCount(snapshot.getSubmitCount())
                .submittedAt(snapshot.getLastSubmittedAt())
                .submissionId(snapshot.getSubmissionId())
                .articleVersion(snapshot.getVersion())
                .lastEventId("reconcile:" + source)
                .build();
        String outcome = transactions.execute(ignored -> applyProjection(req, assignment));
        logProjection(req, outcome);
        return ReviewReconciliationResp.Item.builder()
                .articleId(snapshot.getArticleId())
                .submissionId(snapshot.getSubmissionId())
                .articleVersion(snapshot.getVersion())
                .outcome(outcome)
                .message(source)
                .build();
    }

    private String applyProjection(ReviewTaskUpsertReq req, Long assignment) {
        ReviewTask current = reviewTaskMapper.selectByArticleIdForUpdate(req.getArticleId());
        String oldSubmission = current == null ? null : current.getSubmissionId();
        ArticleStatus oldStatus = current == null ? null : current.getStatus();
        long oldVersion = current == null || current.getLastAppliedVersion() == null
                ? -1L : current.getLastAppliedVersion();
        Long oldAssignment = current == null ? null : current.getAssignedAdminId();

        if (current != null && req.getArticleVersion() < oldVersion) {
            return "STALE_IGNORED";
        }
        if (current != null && req.getArticleVersion() == oldVersion) {
            boolean exactStateDuplicate = Objects.equals(req.getSubmissionId(), oldSubmission)
                    && req.getStatus() == oldStatus;
            if (!exactStateDuplicate) {
                return "VERSION_CONFLICT_IGNORED";
            }
            if (oldStatus == ArticleStatus.PENDING && oldAssignment == null && assignment != null) {
                current.setAssignedAdminId(assignment);
                reviewTaskMapper.updateById(current);
                return "ASSIGNMENT_REPAIRED";
            }
            return "UNCHANGED";
        }

        boolean newPendingGeneration = req.getStatus() == ArticleStatus.PENDING
                && !Objects.equals(oldSubmission, req.getSubmissionId());
        ReviewTask target = current == null ? new ReviewTask() : current;
        target.setArticleId(req.getArticleId());
        if (req.getAuthorId() != null) target.setAuthorId(req.getAuthorId());
        if (req.getTitle() != null) target.setTitle(req.getTitle());
        if (req.getWordCount() != null) target.setWordCount(req.getWordCount());
        if (req.getSubmitCount() != null) target.setSubmitCount(req.getSubmitCount());
        if (req.getSubmittedAt() != null) target.setSubmittedAt(req.getSubmittedAt());
        target.setStatus(req.getStatus() == null ? ArticleStatus.DRAFT : req.getStatus());
        target.setSubmissionId(req.getSubmissionId());
        target.setLastAppliedVersion(req.getArticleVersion());
        target.setLastEventId(req.getLastEventId());

        if (newPendingGeneration) {
            target.setAssignedAdminId(assignment);
            target.setDecisionId(null);
            target.setCommandState("OPEN");
        } else if (target.getStatus() != ArticleStatus.PENDING) {
            target.setAssignedAdminId(null);
            target.setCommandState("CLOSED");
        } else if (target.getCommandState() == null) {
            target.setCommandState("OPEN");
        }

        if (current == null) {
            try {
                reviewTaskMapper.insert(target);
                return "CREATED";
            } catch (DuplicateKeyException race) {
                throw BusinessException.conflict("Concurrent review projection update; retry the same event");
            }
        }
        reviewTaskMapper.updateById(target);
        return "REPAIRED";
    }

    private void logProjection(ReviewTaskUpsertReq req, String outcome) {
        if ("STALE_IGNORED".equals(outcome) || "VERSION_CONFLICT_IGNORED".equals(outcome)) {
            log.warn("Review projection ignored: articleId={}, submissionId={}, version={}, status={}, outcome={}",
                    req.getArticleId(), req.getSubmissionId(), req.getArticleVersion(), req.getStatus(), outcome);
        } else if (!"UNCHANGED".equals(outcome)) {
            log.info("Review projection applied: articleId={}, submissionId={}, version={}, status={}, outcome={}",
                    req.getArticleId(), req.getSubmissionId(), req.getArticleVersion(), req.getStatus(), outcome);
        }
    }

    private Long selectAssignee(Long authorId, String submissionId) {
        List<UserSummaryDto> admins = ResultUtils.requireOk(authUserQueryClient.listReviewAdmins());
        List<Long> eligible = admins == null ? List.of() : admins.stream()
                .map(UserSummaryDto::getId)
                .filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, authorId))
                .sorted(Comparator.naturalOrder())
                .toList();
        if (eligible.isEmpty()) return null;
        return eligible.get(Math.floorMod(submissionId.hashCode(), eligible.size()));
    }

    private void requireVersioned(Long articleId, String submissionId, Long articleVersion) {
        if (articleId == null || submissionId == null || submissionId.isBlank()
                || articleVersion == null || articleVersion < 0) {
            throw BusinessException.badRequest(
                    "Versioned review projection requires articleId, submissionId and articleVersion");
        }
    }
}
