package com.platform.content.service;

import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.content.entity.Article;
import com.platform.content.entity.ContentReviewDecision;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.mapper.ContentReviewDecisionMapper;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.context.TraceContextHolder;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewDecisionService {

    private static final Set<ReviewAction> FINAL_ACTIONS =
            Set.of(ReviewAction.APPROVE, ReviewAction.RETURN, ReviewAction.REJECT);

    private final ArticleMapper articleMapper;
    private final ContentReviewDecisionMapper decisionMapper;
    private final EventOutboxService eventOutboxService;
    private final HomeService homeService;

    @Transactional(rollbackFor = Exception.class)
    public ReviewDecisionResultDto apply(Long articleId, ApplyReviewResultReq req) {
        validate(articleId, req);

        ContentReviewDecision requested = new ContentReviewDecision();
        requested.setDecisionId(req.getDecisionId());
        requested.setArticleId(articleId);
        requested.setSubmissionId(req.getSubmissionId());
        requested.setExpectedVersion(req.getExpectedVersion());
        requested.setAdminId(req.getAdminId());
        requested.setAction(req.getAction());
        requested.setReason(req.getReason());

        decisionMapper.insertProcessing(requested);
        ContentReviewDecision decision = decisionMapper.selectByDecisionIdForUpdate(req.getDecisionId());
        if (decision == null) {
            throw BusinessException.serverError("Decision idempotency record disappeared");
        }
        requireSameBinding(decision, requested);
        if (!"PROCESSING".equals(decision.getState())) {
            return toDto(decision);
        }

        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article != null && Objects.equals(article.getAuthorId(), req.getAdminId())) {
            complete(decision, "CONFLICT", article);
            log.info("Reject author self-review: articleId={}, decisionId={}, adminId={}",
                    articleId, req.getDecisionId(), req.getAdminId());
            return toDto(decision);
        }
        if (!matchesPendingGeneration(article, req)) {
            complete(decision, "CONFLICT", article);
            log.info("Reject stale review command: articleId={}, decisionId={}, submissionId={}, expectedVersion={}",
                    articleId, req.getDecisionId(), req.getSubmissionId(), req.getExpectedVersion());
            return toDto(decision);
        }

        ArticleStatus nextStatus = nextStatus(req.getAction());
        int updated = articleMapper.applyReviewDecisionCas(
                articleId, req.getSubmissionId(), req.getExpectedVersion(), nextStatus);
        if (updated != 1) {
            Article current = articleMapper.selectByIdIncludingDeleted(articleId);
            complete(decision, "CONFLICT", current);
            return toDto(decision);
        }

        Article saved = articleMapper.selectByIdIncludingDeleted(articleId);
        if (saved == null) {
            throw BusinessException.serverError("Accepted review decision could not reload article");
        }
        complete(decision, "FINAL", saved);
        eventOutboxService.saveEvent(
                "article",
                String.valueOf(articleId),
                EventConstants.ARTICLE_STATUS_CHANGED,
                statusEvent(article, saved, decision)
        );
        if (nextStatus == ArticleStatus.APPROVED) {
            afterCommit(homeService::invalidateHeroCache);
        }
        log.info("Accepted review command: articleId={}, decisionId={}, status={}, version={}",
                articleId, decision.getDecisionId(), saved.getStatus(), saved.getVersion());
        return toDto(decision);
    }

    public ReviewDecisionResultDto get(Long articleId, String decisionId) {
        if (articleId == null || decisionId == null || decisionId.isBlank()) {
            throw BusinessException.badRequest("articleId and decisionId are required");
        }
        ContentReviewDecision decision = decisionMapper.selectById(decisionId);
        if (decision == null || !articleId.equals(decision.getArticleId())) {
            throw BusinessException.notFound("Review decision result not found");
        }
        return toDto(decision);
    }

    private void validate(Long articleId, ApplyReviewResultReq req) {
        if (articleId == null || req == null) {
            throw BusinessException.badRequest("Review decision command is required");
        }
        requireKey(req.getDecisionId(), "decisionId");
        requireKey(req.getSubmissionId(), "submissionId");
        if (req.getExpectedVersion() == null || req.getExpectedVersion() < 0) {
            throw BusinessException.badRequest("expectedVersion is required");
        }
        if (req.getAdminId() == null) {
            throw BusinessException.badRequest("adminId is required");
        }
        if (req.getAction() == null || !FINAL_ACTIONS.contains(req.getAction())) {
            throw BusinessException.badRequest("Unsupported review action");
        }
        if (req.getReason() != null && req.getReason().length() > 500) {
            throw BusinessException.badRequest("reason length cannot exceed 500");
        }
        if ((req.getAction() == ReviewAction.RETURN || req.getAction() == ReviewAction.REJECT)
                && (req.getReason() == null || req.getReason().isBlank())) {
            throw BusinessException.badRequest("reason is required for return or reject");
        }
    }

    private void requireKey(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw BusinessException.badRequest(field + " must contain 1 to 64 characters");
        }
    }

    private void requireSameBinding(ContentReviewDecision stored, ContentReviewDecision requested) {
        boolean same = Objects.equals(stored.getArticleId(), requested.getArticleId())
                && Objects.equals(stored.getSubmissionId(), requested.getSubmissionId())
                && Objects.equals(stored.getExpectedVersion(), requested.getExpectedVersion())
                && Objects.equals(stored.getAdminId(), requested.getAdminId())
                && stored.getAction() == requested.getAction()
                && Objects.equals(stored.getReason(), requested.getReason());
        if (!same) {
            throw BusinessException.conflict("decisionId is already bound to a different command payload");
        }
    }

    private boolean matchesPendingGeneration(Article article, ApplyReviewResultReq req) {
        return article != null
                && !Integer.valueOf(1).equals(article.getDeleted())
                && article.getStatus() == ArticleStatus.PENDING
                && Objects.equals(article.getSubmissionId(), req.getSubmissionId())
                && Objects.equals(article.getVersion(), req.getExpectedVersion());
    }

    private ArticleStatus nextStatus(ReviewAction action) {
        return switch (action) {
            case APPROVE -> ArticleStatus.APPROVED;
            case RETURN -> ArticleStatus.RETURNED;
            case REJECT -> ArticleStatus.REJECTED;
            default -> throw BusinessException.badRequest("Unsupported review action");
        };
    }

    private void complete(ContentReviewDecision decision, String state, Article article) {
        LocalDateTime updatedAt = article != null && article.getUpdatedAt() != null
                ? article.getUpdatedAt() : LocalDateTime.now();
        ArticleStatus status = article == null ? null : article.getStatus();
        Long version = article == null ? null : article.getVersion();
        if (decisionMapper.updateOutcome(decision.getDecisionId(), state, status, version, updatedAt) != 1) {
            throw BusinessException.serverError("Failed to persist review decision result");
        }
        decision.setState(state);
        decision.setStatus(status);
        decision.setArticleVersion(version);
        decision.setUpdatedAt(updatedAt);
    }

    private ArticleStatusChangedEvent statusEvent(Article before,
                                                   Article after,
                                                   ContentReviewDecision decision) {
        return ArticleStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .traceId(TraceContextHolder.get())
                .articleId(after.getId())
                .authorId(after.getAuthorId())
                .fromStatus(before.getStatus())
                .toStatus(after.getStatus())
                .title(after.getTitle())
                .summary(after.getSummary())
                .coverUrl(after.getCoverUrl())
                .coverColor(after.getCoverColor())
                .readMinutes(after.getReadMinutes())
                .durationCategory(after.getDurationCategory())
                .publishedAt(after.getPublishedAt())
                .updatedAt(after.getUpdatedAt())
                .submissionId(after.getSubmissionId())
                .articleVersion(after.getVersion())
                .deleted(Integer.valueOf(1).equals(after.getDeleted()))
                .decisionId(decision.getDecisionId())
                .adminId(decision.getAdminId())
                .action(decision.getAction())
                .reason(decision.getReason())
                .build();
    }

    private ReviewDecisionResultDto toDto(ContentReviewDecision decision) {
        return ReviewDecisionResultDto.builder()
                .decisionId(decision.getDecisionId())
                .articleId(decision.getArticleId())
                .submissionId(decision.getSubmissionId())
                .state(decision.getState())
                .status(decision.getStatus())
                .version(decision.getArticleVersion())
                .updatedAt(decision.getUpdatedAt())
                .adminId(decision.getAdminId())
                .action(decision.getAction())
                .reason(decision.getReason())
                .build();
    }

    private void afterCommit(Runnable action) {
        Runnable bestEffort = () -> {
            try {
                action.run();
            } catch (RuntimeException cacheFailure) {
                log.warn("Post-commit cache invalidation failed: {}", cacheFailure.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            bestEffort.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                bestEffort.run();
            }
        });
    }
}