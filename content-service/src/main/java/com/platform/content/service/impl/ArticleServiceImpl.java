package com.platform.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.contract.content.dto.UserProfileArticleItemDto;
import com.platform.contract.content.dto.UserProfileArticleStatsDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.contract.content.dto.WritingCalendarDayDto;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.contract.review.dto.BatchLatestReviewReasonReq;
import com.platform.contract.review.dto.LatestReviewReasonDto;
import com.platform.contract.review.dto.ReviewAssignmentDto;
import com.platform.content.api.resp.ArticleDetailResp;
import com.platform.content.api.resp.SubmitResp;
import com.platform.content.entity.Article;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.ArticleService;
import com.platform.content.util.ArticleUtils;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.context.TraceContextHolder;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.kernel.exception.BusinessException;
import com.platform.kernel.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private static final Set<ArticleStatus> DELETABLE_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.APPROVED, ArticleStatus.RETURNED, ArticleStatus.REJECTED);
    private static final Set<ArticleStatus> SUBMITTABLE_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.RETURNED);
    private static final int MIN_CONTENT_LENGTH = 50;
    private static final int MAX_DRAFT_BOX_ARTICLES = 100;

    private final ArticleMapper articleMapper;
    private final AuthUserQueryClient authInternalClient;
    private final ReviewReasonClient reviewInternalClient;
    private final ReviewTaskClient reviewTaskInternalClient;
    private final EventOutboxService eventOutboxService;
    private final com.platform.content.service.ReviewDecisionService reviewDecisionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createArticle(Long userId) {
        if (userId == null) {
            throw BusinessException.unauthorized("Authentication required");
        }
        articleMapper.ensureAuthorLock(userId);
        if (articleMapper.lockAuthor(userId) == null) {
            throw BusinessException.serverError("Author draft lock could not be acquired");
        }
        if (articleMapper.countDraftBoxByAuthor(userId) >= MAX_DRAFT_BOX_ARTICLES) {
            throw BusinessException.conflict("Draft limit reached: at most " + MAX_DRAFT_BOX_ARTICLES + " articles");
        }

        Article article = new Article();
        article.setAuthorId(userId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setSubmitCount(0);
        article.setWordCount(0);
        article.setReadMinutes(BigDecimal.ZERO);
        article.setDurationCategory(DurationCategory.QUICK);
        article.setDraftVisible(false);
        article.setVersion(0L);
        article.setDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        article.setCreatedAt(now);
        article.setUpdatedAt(now);
        articleMapper.insert(article);

        log.info("Create database draft: userId={}, articleId={}", userId, article.getId());
        return Map.of(
                "id", article.getId(),
                "status", ArticleStatus.DRAFT,
                "version", article.getVersion()
        );
    }

    @Override
    public ArticleDetailResp getArticleDetail(Long articleId, Long currentUserId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }

        Long assignedAdminId = resolveReadAssignment(article, currentUserId);
        UserSummaryDto author = fetchUser(article.getAuthorId());
        String latestReason = null;
        if (article.getStatus() == ArticleStatus.RETURNED
                || article.getStatus() == ArticleStatus.REJECTED) {
            latestReason = getLatestReviewReason(articleId);
        }

        return ArticleDetailResp.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .summary(article.getSummary())
                .coverUrl(article.getCoverUrl())
                .coverColor(article.getCoverColor())
                .wordCount(article.getWordCount())
                .readMinutes(article.getReadMinutes())
                .durationCategory(article.getDurationCategory())
                .status(article.getStatus())
                .version(article.getVersion())
                .draftVisible(Boolean.TRUE.equals(article.getDraftVisible()))
                .submissionId(article.getSubmissionId())
                .assignedAdminId(assignedAdminId)
                .author(ArticleDetailResp.AuthorInfo.builder()
                        .id(author != null ? author.getId() : null)
                        .username(author != null ? author.getUsername() : null)
                        .avatarUrl(author != null ? author.getAvatarUrl() : null)
                        .build())
                .latestReviewReason(latestReason)
                .submitCount(article.getSubmitCount())
                .lastSubmittedAt(article.getLastSubmittedAt())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .publishedAt(article.getPublishedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResp submitForReview(Long articleId, Long userId) {
        Article article = requiredForUpdate(articleId);
        requireNotDeleted(article);
        checkOwnership(article, userId);
        if (!SUBMITTABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict("Article status does not allow submission: " + article.getStatus());
        }
        if (article.getContent() == null || article.getContent().length() < MIN_CONTENT_LENGTH) {
            throw BusinessException.badRequest(
                    "Content must be at least " + MIN_CONTENT_LENGTH + " characters before submit");
        }

        int serverWordCount = ArticleUtils.countWords(article.getContent());
        if (serverWordCount > DraftServiceImpl.MAX_WORD_COUNT) {
            throw BusinessException.badRequest(
                    "Content word count cannot exceed " + DraftServiceImpl.MAX_WORD_COUNT);
        }

        Long expectedVersion = versionOf(article);
        String submissionId = UUID.randomUUID().toString();
        if (articleMapper.submitForReview(articleId, userId, expectedVersion, submissionId) != 1) {
            throw staleWrite("Article changed while submitting; refresh and retry");
        }
        Article saved = requiredIncludingDeleted(articleId);
        eventOutboxService.saveEvent(
                "article",
                String.valueOf(articleId),
                EventConstants.ARTICLE_SUBMITTED,
                ArticleSubmittedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .traceId(TraceContextHolder.get())
                        .articleId(articleId)
                        .authorId(saved.getAuthorId())
                        .submitCount(saved.getSubmitCount())
                        .submittedAt(saved.getLastSubmittedAt())
                        .submissionId(saved.getSubmissionId())
                        .articleVersion(saved.getVersion())
                        .title(saved.getTitle())
                        .wordCount(saved.getWordCount())
                        .build()
        );

        return SubmitResp.builder()
                .status(saved.getStatus())
                .submitCount(saved.getSubmitCount())
                .submissionId(saved.getSubmissionId())
                .version(saved.getVersion())
                .lastSubmittedAt(saved.getLastSubmittedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelReview(Long articleId, Long userId) {
        Article article = requiredForUpdate(articleId);
        requireNotDeleted(article);
        checkOwnership(article, userId);
        if (article.getStatus() != ArticleStatus.PENDING) {
            throw BusinessException.conflict("Only pending articles can cancel review");
        }
        requireSubmission(article);

        if (articleMapper.cancelReview(articleId, userId, versionOf(article), article.getSubmissionId()) != 1) {
            throw staleWrite("Article changed while cancelling review; refresh and retry");
        }
        Article saved = requiredIncludingDeleted(articleId);
        saveStatusEvent(article, saved, null, null, ReviewAction.CANCEL, null);
        return Map.of(
                "status", saved.getStatus(),
                "submissionId", saved.getSubmissionId(),
                "version", saved.getVersion(),
                "updatedAt", saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long articleId, Long userId) {
        Article article = requiredForUpdate(articleId);
        requireNotDeleted(article);
        checkOwnership(article, userId);
        if (!DELETABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict("Article status does not allow delete: " + article.getStatus());
        }
        if (articleMapper.deleteByAuthorCas(articleId, userId, versionOf(article)) != 1) {
            throw staleWrite("Article changed while deleting; refresh and retry");
        }
        Article saved = requiredIncludingDeleted(articleId);
        saveStatusEvent(article, saved, null, null, null, "AUTHOR_DELETE");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> adminDeleteArticle(Long articleId, Long adminId) {
        if (!SecurityUtils.isAdmin()) {
            throw BusinessException.forbidden("Access denied");
        }
        Article article = requiredForUpdate(articleId);
        requireNotDeleted(article);
        if (articleMapper.deleteByAdminCas(articleId, versionOf(article)) != 1) {
            throw staleWrite("Article changed while deleting; refresh and retry");
        }
        Article saved = requiredIncludingDeleted(articleId);
        saveStatusEvent(article, saved, null, adminId, null, "ADMIN_DELETE");
        return Map.of(
                "ok", true,
                "submissionId", saved.getSubmissionId() == null ? "" : saved.getSubmissionId(),
                "version", saved.getVersion(),
                "updatedAt", saved.getUpdatedAt()
        );
    }

    @Override
    public ArticleReviewSnapshotDto getReviewSnapshot(Long articleId) {
        Article article = articleMapper.selectByIdIncludingDeleted(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }
        return toReviewSnapshot(article);
    }

    @Override
    public List<ArticleReviewSnapshotDto> getPendingReviewSnapshots(long afterId, int limit) {
        if (afterId < 0) {
            throw BusinessException.badRequest("afterId must be non-negative");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return articleMapper.selectPendingReviewSnapshots(afterId, boundedLimit).stream()
                .map(this::toReviewSnapshot)
                .toList();
    }

    @Override
    public ReviewDecisionResultDto applyReviewResult(Long articleId, ApplyReviewResultReq req) {
        ReviewDecisionResultDto result = reviewDecisionService.apply(articleId, req);
        if ("CONFLICT".equals(result.getState())) {
            throw BusinessException.conflict("Review command does not match the current pending submission");
        }
        return result;
    }

    @Override
    public ReviewDecisionResultDto getReviewDecisionResult(Long articleId, String decisionId) {
        return reviewDecisionService.get(articleId, decisionId);
    }

    @Override
    public void applyReviewDecisionEvent(ReviewDecidedEvent event) {
        if (event == null
                || event.getArticleId() == null
                || event.getDecisionId() == null || event.getDecisionId().isBlank()
                || event.getSubmissionId() == null || event.getSubmissionId().isBlank()
                || event.getExpectedVersion() == null) {
            throw BusinessException.badRequest("Versioned review command fields are required");
        }
        ApplyReviewResultReq req = new ApplyReviewResultReq();
        req.setDecisionId(event.getDecisionId());
        req.setSubmissionId(event.getSubmissionId());
        req.setExpectedVersion(event.getExpectedVersion());
        req.setAdminId(event.getAdminId());
        req.setAction(event.getAction());
        req.setReason(event.getReason());
        // Durable command conflicts are authoritative terminal outcomes and are acknowledged by the listener.
        reviewDecisionService.apply(event.getArticleId(), req);
    }

    @Override
    public UserProfileArticlesResp getUserProfileArticles(UserProfileArticlesQueryReq req) {
        if (req == null || req.getAuthorId() == null) {
            throw BusinessException.badRequest("authorId is required");
        }

        int page = req.getPage() <= 0 ? 1 : req.getPage();
        int requestedLimit = req.getLimit() <= 0 ? 20 : req.getLimit();
        Long authorId = req.getAuthorId();
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean canViewAll = authorId.equals(currentUserId) || SecurityUtils.isAdmin();
        int pageSize = req.getPageSize() <= 0 ? requestedLimit : req.getPageSize();
        pageSize = Math.max(1, Math.min(pageSize, canViewAll ? 100 : 50));
        ArticleStatus filterStatus = resolveProfileTabStatus(req.getTab(), canViewAll);
        UserSummaryDto author = fetchUser(authorId);
        UserProfileArticleStatsDto stats = articleMapper.selectProfileStats(authorId, canViewAll);
        if (stats == null) {
            stats = UserProfileArticleStatsDto.builder().build();
        }
        LocalDateTime since = LocalDate.now().minusYears(3).atStartOfDay();
        List<WritingCalendarDayDto> writingCalendar = canViewAll
                ? articleMapper.selectWritingCalendar(authorId, since)
                : articleMapper.selectApprovedWritingCalendar(authorId, since);

        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .orderByDesc(Article::getUpdatedAt)
                .orderByDesc(Article::getId);
        if (filterStatus != null) {
            query.eq(Article::getStatus, filterStatus);
        } else if (!canViewAll) {
            query.eq(Article::getStatus, ArticleStatus.APPROVED);
        }

        IPage<Article> pageResult = articleMapper.selectPage(new Page<>(page, pageSize), query);
        List<Article> articles = pageResult.getRecords();
        Map<Long, String> rejectReasonMap = buildReviewReasonMap(articles);
        List<UserProfileArticleItemDto> list = articles.stream()
                .map(article -> UserProfileArticleItemDto.builder()
                        .articleId(article.getId())
                        .title(article.getTitle())
                        .summary(article.getSummary())
                        .previewText(article.getContent() == null ? null
                                : ArticleUtils.extractPreviewText(article.getContent(), 120))
                        .coverUrl(article.getCoverUrl())
                        .coverColor(article.getCoverColor())
                        .readMinutes(article.getReadMinutes())
                        .durationCategory(article.getDurationCategory())
                        .status(article.getStatus())
                        .authorId(authorId)
                        .authorUsername(author != null ? author.getUsername() : null)
                        .authorName(author != null ? author.getNickname() : null)
                        .wordCount(article.getWordCount())
                        .draftVisible(Boolean.TRUE.equals(article.getDraftVisible()))
                        .authorAvatar(author != null ? author.getAvatarUrl() : null)
                        .publishedAt(article.getPublishedAt())
                        .updatedAt(article.getUpdatedAt())
                        .rejectReason(rejectReasonMap.get(article.getId()))
                        .build())
                .toList();

        return UserProfileArticlesResp.builder()
                .stats(stats)
                .writingCalendar(writingCalendar == null ? List.of() : writingCalendar)
                .list(list)
                .total(pageResult.getTotal())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    private Long resolveReadAssignment(Article article, Long currentUserId) {
        if (article.getStatus() == ArticleStatus.APPROVED) {
            return null;
        }
        boolean author = currentUserId != null && currentUserId.equals(article.getAuthorId());
        if (article.getStatus() != ArticleStatus.PENDING) {
            if (!author) {
                throw BusinessException.notFound("Article not found");
            }
            return null;
        }
        requireSubmission(article);
        if (!author && (currentUserId == null || !SecurityUtils.isAdmin())) {
            throw BusinessException.notFound("Article not found");
        }

        ReviewAssignmentDto assignment;
        try {
            assignment = ResultUtils.requireOk(
                    reviewTaskInternalClient.assignment(article.getId(), article.getSubmissionId()));
        } catch (RuntimeException remoteFailure) {
            if (author) {
                log.warn("Pending assignment unavailable for author read: articleId={}, submissionId={}, error={}",
                        article.getId(), article.getSubmissionId(), remoteFailure.getMessage());
                return null;
            }
            throw remoteFailure;
        }
        if (assignment != null
                && (!article.getId().equals(assignment.getArticleId())
                || !article.getSubmissionId().equals(assignment.getSubmissionId()))) {
            if (author) {
                log.warn("Ignore mismatched assignment for author read: articleId={}, submissionId={}",
                        article.getId(), article.getSubmissionId());
                return null;
            }
            throw BusinessException.serverError("Review assignment response does not match article generation");
        }
        Long assignedAdminId = assignment == null ? null : assignment.getAssignedAdminId();
        if (!author && !currentUserId.equals(assignedAdminId)) {
            throw BusinessException.notFound("Article not found");
        }
        return assignedAdminId;
    }
    private ArticleReviewSnapshotDto toReviewSnapshot(Article article) {
        return ArticleReviewSnapshotDto.builder()
                .articleId(article.getId())
                .authorId(article.getAuthorId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .content(article.getContent())
                .wordCount(article.getWordCount())
                .submitCount(article.getSubmitCount())
                .status(article.getStatus())
                .lastSubmittedAt(article.getLastSubmittedAt())
                .submissionId(article.getSubmissionId())
                .version(article.getVersion())
                .deleted(Integer.valueOf(1).equals(article.getDeleted()))
                .updatedAt(article.getUpdatedAt())
                .build();
    }

    private void saveStatusEvent(Article before,
                                 Article after,
                                 String decisionId,
                                 Long adminId,
                                 ReviewAction action,
                                 String reason) {
        eventOutboxService.saveEvent(
                "article",
                String.valueOf(after.getId()),
                EventConstants.ARTICLE_STATUS_CHANGED,
                ArticleStatusChangedEvent.builder()
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
                        .decisionId(decisionId)
                        .adminId(adminId)
                        .action(action)
                        .reason(reason)
                        .build()
        );
    }

    private Article requiredForUpdate(Long articleId) {
        Article article = articleMapper.selectByIdForUpdate(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }
        return article;
    }

    private Article requiredIncludingDeleted(Long articleId) {
        Article article = articleMapper.selectByIdIncludingDeleted(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }
        return article;
    }

    private void requireNotDeleted(Article article) {
        if (Integer.valueOf(1).equals(article.getDeleted())) {
            throw BusinessException.notFound("Article not found");
        }
    }

    private void requireSubmission(Article article) {
        if (article.getSubmissionId() == null || article.getSubmissionId().isBlank()) {
            throw BusinessException.conflict("Pending article has no submission generation");
        }
    }

    private Long versionOf(Article article) {
        if (article.getVersion() == null || article.getVersion() < 0) {
            throw BusinessException.serverError("Article version is unavailable");
        }
        return article.getVersion();
    }

    private void checkOwnership(Article article, Long userId) {
        if (userId == null || !userId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("Access denied");
        }
    }

    private BusinessException staleWrite(String message) {
        return BusinessException.conflict(message);
    }

    private String getLatestReviewReason(Long articleId) {
        LatestReviewReasonDto dto = ResultUtils.requireOk(reviewInternalClient.latestReason(articleId));
        return dto != null ? dto.getReason() : null;
    }

    private ArticleStatus resolveProfileTabStatus(String tab, boolean canViewAll) {
        if (!canViewAll) {
            return ArticleStatus.APPROVED;
        }
        if (tab == null || tab.equalsIgnoreCase("all")) {
            return null;
        }
        return switch (tab.toLowerCase()) {
            case "approved" -> ArticleStatus.APPROVED;
            case "pending" -> ArticleStatus.PENDING;
            case "returned" -> ArticleStatus.RETURNED;
            case "rejected" -> ArticleStatus.REJECTED;
            case "draft" -> ArticleStatus.DRAFT;
            default -> null;
        };
    }

    private Map<Long, String> buildReviewReasonMap(List<Article> articles) {
        if (articles.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = articles.stream()
                .filter(article -> article.getStatus() == ArticleStatus.RETURNED
                        || article.getStatus() == ArticleStatus.REJECTED)
                .map(Article::getId)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LatestReviewReasonDto> reasons = ResultUtils.requireOk(
                reviewInternalClient.batchLatestReasons(new BatchLatestReviewReasonReq(ids)));
        Map<Long, String> result = new HashMap<>();
        if (reasons != null) {
            reasons.stream()
                    .filter(reason -> reason.getReason() != null)
                    .forEach(reason -> result.put(reason.getArticleId(), reason.getReason()));
        }
        return result;
    }

    private UserSummaryDto fetchUser(Long userId) {
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(
                new BatchUserQueryReq(Collections.singletonList(userId))));
        return users == null || users.isEmpty() ? null : users.get(0);
    }
}
