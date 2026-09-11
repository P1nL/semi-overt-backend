package com.platform.review.service.impl;

import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.context.TraceContextHolder;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ReviewDecisionPayload;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.kernel.exception.BusinessException;
import com.platform.review.entity.ReviewCommand;
import com.platform.review.entity.ReviewLog;
import com.platform.review.entity.ReviewTask;
import com.platform.review.mapper.ReviewCommandMapper;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.ReviewDecisionCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReviewDecisionCoordinatorImpl implements ReviewDecisionCoordinator {

    public static final String PROCESSING = "PROCESSING";
    public static final String FINAL = "FINAL";
    public static final String CONFLICT = "CONFLICT";

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewCommandMapper reviewCommandMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final EventOutboxService eventOutboxService;
    private final TransactionTemplate claimTransactions;
    private final TransactionTemplate finalizeTransactions;

    public ReviewDecisionCoordinatorImpl(ReviewTaskMapper reviewTaskMapper,
                                         ReviewCommandMapper reviewCommandMapper,
                                         ReviewLogMapper reviewLogMapper,
                                         EventOutboxService eventOutboxService,
                                         PlatformTransactionManager transactionManager) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewCommandMapper = reviewCommandMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.eventOutboxService = eventOutboxService;
        this.claimTransactions = new TransactionTemplate(transactionManager);
        this.claimTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.finalizeTransactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public ReviewCommand claim(Long articleId, Long adminId, String requestedDecisionId,
                               String actionValue, String reasonValue,
                               String requestedSubmissionId, Long requestedExpectedVersion) {
        ReviewAction action = parseAction(actionValue);
        String reason = normalizeReason(reasonValue);
        validateReason(action, reason);
        validateBaselinePair(requestedSubmissionId, requestedExpectedVersion);
        String decisionId = normalizeRequestedDecisionId(requestedDecisionId);
        if (decisionId != null) {
            ReviewCommand existingHint = reviewCommandMapper.selectById(decisionId);
            if (existingHint != null && !Objects.equals(existingHint.getArticleId(), articleId)) {
                throw BusinessException.conflict("The same decisionId was already bound to another article");
            }
        }
        return claimTransactions.execute(ignored ->
                claimInTransaction(articleId, adminId, decisionId, action, reason,
                        requestedSubmissionId, requestedExpectedVersion));
    }

    private ReviewCommand claimInTransaction(Long articleId, Long adminId, String requestedDecisionId,
                                             ReviewAction action, String reason,
                                             String requestedSubmissionId, Long requestedExpectedVersion) {
        if (articleId == null || adminId == null) {
            throw BusinessException.badRequest("articleId and authenticated adminId are required");
        }

        // Every path locks task before command. Finalization uses the same order.
        ReviewTask task = reviewTaskMapper.selectByArticleIdForUpdate(articleId);
        if (requestedDecisionId != null) {
            ReviewCommand prior = reviewCommandMapper.selectForUpdate(requestedDecisionId);
            if (prior != null) {
                assertSameClientPayload(prior, articleId, adminId, action, reason);
                assertRequestedBaseline(prior.getSubmissionId(), prior.getExpectedVersion(),
                        requestedSubmissionId, requestedExpectedVersion);
                return prior;
            }
        }

        if (task == null) {
            throw BusinessException.conflict("Review task tombstone not found");
        }

        assertRequestedBaseline(task.getSubmissionId(), task.getLastAppliedVersion(),
                requestedSubmissionId, requestedExpectedVersion);

        if (task.getStatus() != ArticleStatus.PENDING) {
            throw BusinessException.conflict("Article is no longer pending review: " + task.getStatus());
        }
        if (isBlank(task.getSubmissionId()) || task.getLastAppliedVersion() == null
                || task.getLastAppliedVersion() < 0) {
            throw BusinessException.conflict("Review task is not versioned and cannot be claimed");
        }
        if (Objects.equals(adminId, task.getAuthorId())) {
            throw BusinessException.forbidden("Administrators cannot review their own article");
        }
        if (task.getAssignedAdminId() == null) {
            throw BusinessException.conflict("Review task is waiting for an eligible administrator assignment");
        }
        if (!Objects.equals(adminId, task.getAssignedAdminId())) {
            throw BusinessException.forbidden("Review task is assigned to another administrator");
        }

        String decisionId = requestedDecisionId == null
                ? stableCompatibilityDecisionId(articleId, task.getSubmissionId(), adminId, action, reason)
                : requestedDecisionId;
        String payloadHash = payloadHash(articleId, task.getSubmissionId(), task.getLastAppliedVersion(),
                adminId, action, reason);

        ReviewCommand priorById = reviewCommandMapper.selectForUpdate(decisionId);
        if (priorById != null) {
            assertHash(priorById, payloadHash);
            return priorById;
        }
        ReviewCommand priorForSubmission = reviewCommandMapper.selectSubmissionForUpdate(
                articleId, task.getSubmissionId());
        if (priorForSubmission != null) {
            if (Objects.equals(priorForSubmission.getDecisionId(), decisionId)) {
                assertHash(priorForSubmission, payloadHash);
                return priorForSubmission;
            }
            throw BusinessException.conflict("This submission already has a different review decision");
        }
        if (task.getDecisionId() != null && !Objects.equals(task.getDecisionId(), decisionId)) {
            throw BusinessException.conflict("This review task is already claimed by a different decision");
        }
        if (task.getCommandState() != null && !"OPEN".equals(task.getCommandState())) {
            throw BusinessException.conflict("Review task is not open for a new decision");
        }

        LocalDateTime now = LocalDateTime.now();
        ReviewCommand command = new ReviewCommand();
        command.setDecisionId(decisionId);
        command.setArticleId(articleId);
        command.setSubmissionId(task.getSubmissionId());
        command.setExpectedVersion(task.getLastAppliedVersion());
        command.setAdminId(adminId);
        command.setAction(action);
        command.setReason(reason);
        command.setPayloadHash(payloadHash);
        command.setState(PROCESSING);
        command.setCreatedAt(now);
        command.setUpdatedAt(now);
        reviewCommandMapper.insert(command);

        task.setDecisionId(decisionId);
        task.setCommandState(PROCESSING);
        reviewTaskMapper.updateById(task);

        ReviewDecisionPayload payload = ReviewDecisionPayload.builder()
                .decisionId(decisionId)
                .articleId(articleId)
                .submissionId(task.getSubmissionId())
                .expectedVersion(task.getLastAppliedVersion())
                .adminId(adminId)
                .action(action)
                .reason(reason)
                .fromStatus(ArticleStatus.PENDING)
                .toStatus(toStatus(action))
                .traceId(TraceContextHolder.get())
                .build();
        eventOutboxService.saveEvent(
                "review",
                decisionId,
                EventConstants.REVIEW_DECIDED,
                ReviewDecidedEvent.fromPayload(UUID.randomUUID().toString(), payload));
        return command;
    }

    @Override
    public ReviewCommand finalizeAuthoritativeResult(ReviewDecisionResultDto result) {
        if (result == null || isBlank(result.getDecisionId()) || result.getArticleId() == null) {
            throw BusinessException.badRequest("Authoritative review result with decisionId and articleId is required");
        }
        return finalizeTransactions.execute(ignored -> finalizeInTransaction(result));
    }

    private ReviewCommand finalizeInTransaction(ReviewDecisionResultDto result) {
        // Fixed lock order: task then command. A missing task is allowed only for legacy damage;
        // the command remains finalizable, but no current projection is closed.
        ReviewTask task = reviewTaskMapper.selectByArticleIdForUpdate(result.getArticleId());
        ReviewCommand command = reviewCommandMapper.selectForUpdate(result.getDecisionId());
        if (command == null) {
            throw BusinessException.notFound("Review command not found");
        }
        assertAuthoritativeIdentity(command, result);

        String resultState = result.getState() == null
                ? "" : result.getState().toUpperCase(Locale.ROOT);
        if (!FINAL.equals(resultState) && !CONFLICT.equals(resultState)) {
            throw BusinessException.conflict("Content result is not terminal");
        }
        if (result.getUpdatedAt() == null) {
            throw BusinessException.conflict("Terminal content result must include authoritative updatedAt");
        }
        if (FINAL.equals(resultState)) {
            validateFinalResult(command, result);
        }

        if (FINAL.equals(command.getState()) || CONFLICT.equals(command.getState())) {
            if (!Objects.equals(command.getState(), resultState)
                    || !Objects.equals(command.getStatus(), result.getStatus())
                    || !Objects.equals(command.getArticleVersion(), result.getVersion())
                    || !Objects.equals(command.getUpdatedAt(), result.getUpdatedAt())) {
                throw BusinessException.conflict("Authoritative result conflicts with retained command result");
            }
            return command;
        }

        command.setState(resultState);
        command.setStatus(result.getStatus());
        command.setArticleVersion(result.getVersion());
        command.setUpdatedAt(result.getUpdatedAt());
        command.setErrorMessage(CONFLICT.equals(resultState) ? "Content rejected review command" : null);
        reviewCommandMapper.updateById(command);

        if (FINAL.equals(resultState)) {
            writeFinalLog(command, result);
        }
        applyResultToTaskTombstone(task, command, result, resultState);
        return command;
    }

    private void validateFinalResult(ReviewCommand command, ReviewDecisionResultDto result) {
        if (result.getStatus() != toStatus(command.getAction())) {
            throw BusinessException.conflict("FINAL content result status does not match the review action");
        }
        if (result.getVersion() == null
                || !Objects.equals(result.getVersion(), command.getExpectedVersion() + 1)) {
            throw BusinessException.conflict("FINAL content result version must equal expectedVersion + 1");
        }
    }

    private void writeFinalLog(ReviewCommand command, ReviewDecisionResultDto result) {
        if (reviewLogMapper.selectByDecisionId(command.getDecisionId()) != null) {
            return;
        }
        ReviewLog log = new ReviewLog();
        log.setArticleId(command.getArticleId());
        log.setOperatorId(command.getAdminId());
        log.setAction(command.getAction());
        log.setFromStatus(ArticleStatus.PENDING);
        log.setToStatus(result.getStatus());
        log.setReason(command.getReason());
        log.setDecisionId(command.getDecisionId());
        log.setSubmissionId(command.getSubmissionId());
        log.setArticleVersion(result.getVersion());
        reviewLogMapper.insert(log);
    }

    private void applyResultToTaskTombstone(ReviewTask task, ReviewCommand command,
                                            ReviewDecisionResultDto result, String resultState) {
        if (task == null || !Objects.equals(task.getSubmissionId(), command.getSubmissionId())) {
            return;
        }
        long taskVersion = task.getLastAppliedVersion() == null ? -1L : task.getLastAppliedVersion();
        if (result.getVersion() != null && result.getVersion() < taskVersion) {
            return;
        }
        if (result.getVersion() != null) task.setLastAppliedVersion(result.getVersion());
        if (result.getStatus() != null) task.setStatus(result.getStatus());
        if (Objects.equals(task.getDecisionId(), command.getDecisionId())) {
            if (FINAL.equals(resultState) || task.getStatus() == ArticleStatus.PENDING) {
                task.setCommandState(resultState);
            }
        }
        reviewTaskMapper.updateById(task);
    }

    @Override
    public ReviewCommand find(String decisionId) {
        if (isBlank(decisionId)) {
            throw BusinessException.badRequest("decisionId is required");
        }
        String normalized = decisionId.trim();
        if (normalized.length() > 64) {
            throw BusinessException.badRequest("decisionId must not exceed 64 characters");
        }
        return reviewCommandMapper.selectById(normalized);
    }

    private void assertSameClientPayload(ReviewCommand prior, Long articleId, Long adminId,
                                         ReviewAction action, String reason) {
        if (!Objects.equals(prior.getArticleId(), articleId)
                || !Objects.equals(prior.getAdminId(), adminId)
                || prior.getAction() != action
                || !Objects.equals(normalizeReason(prior.getReason()), reason)) {
            throw BusinessException.conflict("The same decisionId was already bound to different review content");
        }
        assertHash(prior, payloadHash(prior.getArticleId(), prior.getSubmissionId(), prior.getExpectedVersion(),
                prior.getAdminId(), prior.getAction(), prior.getReason()));
    }

    private void assertAuthoritativeIdentity(ReviewCommand command, ReviewDecisionResultDto result) {
        if (!Objects.equals(command.getArticleId(), result.getArticleId())
                || !Objects.equals(command.getSubmissionId(), result.getSubmissionId())
                || !Objects.equals(command.getAdminId(), result.getAdminId())
                || command.getAction() != result.getAction()
                || !Objects.equals(normalizeReason(command.getReason()), normalizeReason(result.getReason()))) {
            throw BusinessException.conflict("Content result identity does not match the retained review command");
        }
    }

    private void assertHash(ReviewCommand command, String expectedHash) {
        if (!Objects.equals(command.getPayloadHash(), expectedHash)) {
            throw BusinessException.conflict("The same decisionId was already bound to different review content");
        }
    }

    private void validateBaselinePair(String submissionId, Long expectedVersion) {
        if ((submissionId == null || submissionId.isBlank()) != (expectedVersion == null)) {
            throw BusinessException.badRequest("submissionId and expectedVersion must be provided together");
        }
        if (expectedVersion != null && expectedVersion < 0) {
            throw BusinessException.badRequest("expectedVersion must not be negative");
        }
    }

    private void assertRequestedBaseline(String actualSubmissionId, Long actualVersion,
                                         String requestedSubmissionId, Long requestedExpectedVersion) {
        if (requestedSubmissionId == null && requestedExpectedVersion == null) return;
        if (!Objects.equals(actualSubmissionId, requestedSubmissionId)
                || !Objects.equals(actualVersion, requestedExpectedVersion)) {
            throw BusinessException.conflict("Review page baseline is stale; reload the assigned task");
        }
    }
    private String normalizeRequestedDecisionId(String value) {
        if (isBlank(value)) return null;
        String trimmed = value.trim();
        if (trimmed.length() > 64) {
            throw BusinessException.badRequest("decisionId must not exceed 64 characters");
        }
        return trimmed;
    }

    private String stableCompatibilityDecisionId(Long articleId, String submissionId, Long adminId,
                                                 ReviewAction action, String reason) {
        String canonical = "review-compat|" + articleId + "|" + submissionId + "|" + adminId
                + "|" + action + "|" + nullSafe(reason);
        return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String payloadHash(Long articleId, String submissionId, Long expectedVersion, Long adminId,
                               ReviewAction action, String reason) {
        String canonical = articleId + "\n" + submissionId + "\n" + expectedVersion + "\n" + adminId
                + "\n" + action + "\n" + nullSafe(normalizeReason(reason));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private ReviewAction parseAction(String actionValue) {
        if (isBlank(actionValue)) {
            throw BusinessException.badRequest("Review action is required");
        }
        try {
            ReviewAction action = ReviewAction.valueOf(actionValue.trim().toUpperCase(Locale.ROOT));
            if (action == ReviewAction.CANCEL) {
                throw BusinessException.badRequest("CANCEL is owned by the article cancel-review flow");
            }
            return action;
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Unsupported review action: " + actionValue);
        }
    }

    private void validateReason(ReviewAction action, String reason) {
        if ((action == ReviewAction.RETURN || action == ReviewAction.REJECT) && reason == null) {
            throw BusinessException.badRequest("Reason is required for RETURN and REJECT");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null) return null;
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullSafe(String value) {
        return value == null ? "<null>" : value;
    }

    private ArticleStatus toStatus(ReviewAction action) {
        return switch (action) {
            case APPROVE -> ArticleStatus.APPROVED;
            case RETURN -> ArticleStatus.RETURNED;
            case REJECT -> ArticleStatus.REJECTED;
            default -> throw BusinessException.badRequest("Unsupported review action");
        };
    }
}
