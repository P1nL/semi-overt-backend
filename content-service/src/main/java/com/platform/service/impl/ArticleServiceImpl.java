package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.client.AuthInternalClient;
import com.platform.client.ReviewInternalClient;
import com.platform.common.api.ResultUtils;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.dto.internal.BatchUserQueryReq;
import com.platform.common.dto.internal.LatestReviewReasonDto;
import com.platform.common.constant.EventConstants;
import com.platform.common.dto.internal.UserProfileArticleItemDto;
import com.platform.common.dto.internal.UserProfileArticleStatsDto;
import com.platform.common.dto.internal.UserProfileArticlesQueryReq;
import com.platform.common.dto.internal.UserProfileArticlesResp;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.common.context.TraceContextHolder;
import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.common.event.ArticleSubmittedEvent;
import com.platform.common.event.ReviewDecidedEvent;
import com.platform.common.support.EventOutboxService;
import com.platform.dto.resp.ArticleDetailResp;
import com.platform.dto.resp.SubmitCooldownResp;
import com.platform.dto.resp.SubmitResp;
import com.platform.entity.Article;
import com.platform.enums.ArticleStatus;
import com.platform.exception.BusinessException;
import com.platform.mapper.ArticleMapper;
import com.platform.service.ArticleService;
import com.platform.util.ArticleUtils;
import com.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private static final String DRAFT_KEY_PREFIX = "draft:";
    private static final Set<ArticleStatus> DELETABLE_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.RETURNED, ArticleStatus.REJECTED);
    private static final Set<ArticleStatus> ADMIN_DELETABLE_STATUSES =
            Set.of(
                    ArticleStatus.DRAFT,
                    ArticleStatus.PENDING,
                    ArticleStatus.APPROVED,
                    ArticleStatus.RETURNED,
                    ArticleStatus.REJECTED
            );
    private static final Set<ArticleStatus> SUBMITTABLE_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.RETURNED);
    private static final int MIN_CONTENT_LENGTH = 50;
    private static final long SUBMIT_COOLDOWN_MINUTES = 30;

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final AuthInternalClient authInternalClient;
    private final ReviewInternalClient reviewInternalClient;
    private final EventOutboxService eventOutboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createArticle(Long userId) {
        Article article = new Article();
        article.setAuthorId(userId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setSubmitCount(0);
        articleMapper.insert(article);

        log.info("Create empty article: userId={}, articleId={}", userId, article.getId());
        return Map.of("id", article.getId(), "status", ArticleStatus.DRAFT);
    }

    @Override
    public ArticleDetailResp getArticleDetail(Long articleId, Long currentUserId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }

        checkReadPermission(article, currentUserId);

        UserSummaryDto author = fetchUser(article.getAuthorId());
        String latestReason = null;
        if (article.getStatus() == ArticleStatus.RETURNED
                || article.getStatus() == ArticleStatus.REJECTED) {
            latestReason = getLatestReviewReason(articleId);
        }

        String content = article.getContent();
        if (article.getStatus() == ArticleStatus.DRAFT
                || article.getStatus() == ArticleStatus.RETURNED) {
            String redisContent = redisTemplate.opsForValue()
                    .get(buildDraftKey(article.getAuthorId(), articleId));
            if (redisContent != null) {
                content = redisContent;
            }
        }

        return ArticleDetailResp.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(content)
                .summary(article.getSummary())
                .coverUrl(article.getCoverUrl())
                .coverColor(article.getCoverColor())
                .wordCount(article.getWordCount())
                .readMinutes(article.getReadMinutes())
                .durationCategory(article.getDurationCategory())
                .status(article.getStatus())
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
        Article article = getArticleByIdForWrite(articleId);

        checkOwnership(article, userId);

        if (!SUBMITTABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict(
                    "Article status does not allow submission: " + article.getStatus());
        }

        String content = getLatestContent(userId, articleId, article.getContent());
        if (content == null || content.length() < MIN_CONTENT_LENGTH) {
            throw BusinessException.badRequest(
                    "Content must be at least " + MIN_CONTENT_LENGTH + " characters before submit");
        }

        if (article.getLastSubmittedAt() != null) {
            LocalDateTime cooldownEnd = article.getLastSubmittedAt()
                    .plusMinutes(SUBMIT_COOLDOWN_MINUTES);
            if (LocalDateTime.now().isBefore(cooldownEnd)) {
                long remainingSeconds = Math.max(1, ChronoUnit.SECONDS.between(LocalDateTime.now(), cooldownEnd));
                throw BusinessException.tooManyRequests(
                        "This article can only be submitted once every 30 minutes",
                        SubmitCooldownResp.builder()
                                .nextSubmitAt(cooldownEnd)
                                .remainingSeconds(remainingSeconds)
                                .build());
            }
        }

        String redisContent = redisTemplate.opsForValue().get(buildDraftKey(userId, articleId));
        if (redisContent != null) {
            article.setContent(redisContent);
        }

        LocalDateTime now = LocalDateTime.now();
        article.setStatus(ArticleStatus.PENDING);
        article.setSubmitCount(article.getSubmitCount() + 1);
        article.setLastSubmittedAt(now);
        articleMapper.updateById(article);
        redisTemplate.delete(buildDraftKey(userId, articleId));
        eventOutboxService.saveEvent(
                "article",
                String.valueOf(articleId),
                EventConstants.ARTICLE_SUBMITTED,
                ArticleSubmittedEvent.builder()
                        .eventId(newEventId("article-submitted", articleId))
                        .traceId(TraceContextHolder.get())
                        .articleId(articleId)
                        .authorId(article.getAuthorId())
                        .submitCount(article.getSubmitCount())
                        .submittedAt(now)
                        .build()
        );

        log.info("Submit article for review: articleId={}, userId={}, submitCount={}",
                articleId, userId, article.getSubmitCount());

        return SubmitResp.builder()
                .status(ArticleStatus.PENDING)
                .submitCount(article.getSubmitCount())
                .lastSubmittedAt(now)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelReview(Long articleId, Long userId) {
        Article article = getArticleByIdForWrite(articleId);

        checkOwnership(article, userId);

        if (article.getStatus() != ArticleStatus.PENDING) {
            throw BusinessException.conflict(
                    "Only pending articles can cancel review, current status: " + article.getStatus());
        }

        ArticleStatus fromStatus = article.getStatus();
        article.setStatus(ArticleStatus.DRAFT);
        articleMapper.updateById(article);
        eventOutboxService.saveEvent(
                "article",
                String.valueOf(articleId),
                EventConstants.ARTICLE_STATUS_CHANGED,
                buildStatusChangedEvent(article, fromStatus, ArticleStatus.DRAFT)
        );

        log.info("Cancel article review: articleId={}, userId={}", articleId, userId);
        return Map.of("status", ArticleStatus.DRAFT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long articleId, Long userId) {
        Article article = getArticleByIdForWrite(articleId);

        checkOwnership(article, userId);

        if (!DELETABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict(
                    "Article status does not allow delete: " + article.getStatus());
        }

        articleMapper.deleteById(articleId);
        redisTemplate.delete(buildDraftKey(userId, articleId));

        log.info("Delete article: articleId={}, userId={}", articleId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> adminDeleteArticle(Long articleId, Long adminId) {
        if (!SecurityUtils.isAdmin()) {
            throw BusinessException.forbidden("Access denied");
        }

        Article article = getArticleByIdForWrite(articleId);

        if (!ADMIN_DELETABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict(
                    "Article status does not allow admin delete: " + article.getStatus());
        }

        articleMapper.deleteById(articleId);
        redisTemplate.delete(buildDraftKey(article.getAuthorId(), articleId));

        log.info("Admin delete article: articleId={}, adminId={}, authorId={}, status={}",
                articleId, adminId, article.getAuthorId(), article.getStatus());

        return Map.of("ok", true);
    }

    @Override
    public ArticleReviewSnapshotDto getReviewSnapshot(Long articleId) {
        Article article = getArticleByIdForWrite(articleId);
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
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyReviewResult(Long articleId, com.platform.common.dto.internal.ApplyReviewResultReq req) {
        Article article = getArticleByIdForWrite(articleId);
        if (article.getStatus() != ArticleStatus.PENDING) {
            throw BusinessException.conflict(
                    "Article status does not allow applying a review result: " + article.getStatus());
        }

        applyReviewDecision(article, req.getAction(), req.getAdminId(), req.getReason(), null);
    }

    @Override
    public UserProfileArticlesResp getUserProfileArticles(UserProfileArticlesQueryReq req) {
        if (req == null || req.getAuthorId() == null) {
            throw BusinessException.badRequest("authorId is required");
        }

        int page = req.getPage() <= 0 ? 1 : req.getPage();
        int pageSize = req.getPageSize() <= 0 ? 10 : req.getPageSize();
        Long authorId = req.getAuthorId();
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean canViewAll = authorId.equals(currentUserId) || SecurityUtils.isAdmin();
        ArticleStatus filterStatus = resolveProfileTabStatus(req.getTab(), canViewAll);
        UserSummaryDto author = fetchUser(authorId);

        UserProfileArticleStatsDto stats = buildProfileStats(authorId, canViewAll);

        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .orderByDesc(Article::getUpdatedAt);

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
                        .authorName(author != null ? author.getNickname() : null)
                        .authorAvatar(author != null ? author.getAvatarUrl() : null)
                        .publishedAt(article.getPublishedAt())
                        .updatedAt(article.getUpdatedAt())
                        .rejectReason(rejectReasonMap.get(article.getId()))
                        .build())
                .toList();

        return UserProfileArticlesResp.builder()
                .stats(stats)
                .list(list)
                .total(pageResult.getTotal())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    private void checkReadPermission(Article article, Long currentUserId) {
        if (article.getStatus() == ArticleStatus.APPROVED) {
            return;
        }

        boolean isAuthor = currentUserId != null
                && currentUserId.equals(article.getAuthorId());

        if (article.getStatus() == ArticleStatus.PENDING) {
            if (!isAuthor && !SecurityUtils.isAdmin()) {
                throw BusinessException.notFound("Article not found");
            }
            return;
        }

        if (!isAuthor) {
            throw BusinessException.notFound("Article not found");
        }
    }

    private void checkOwnership(Article article, Long userId) {
        if (!userId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("Access denied");
        }
    }

    private Article getArticleByIdForWrite(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }
        return article;
    }

    private String getLatestContent(Long userId, Long articleId, String mysqlContent) {
        String redisContent = redisTemplate.opsForValue().get(buildDraftKey(userId, articleId));
        return redisContent != null ? redisContent : mysqlContent;
    }

    private String getLatestReviewReason(Long articleId) {
        LatestReviewReasonDto dto = ResultUtils.requireOk(reviewInternalClient.latestReason(articleId));
        return dto != null ? dto.getReason() : null;
    }

    public void applyReviewDecisionEvent(ReviewDecidedEvent event) {
        Article article = getArticleByIdForWrite(event.getArticleId());
        if (article.getStatus() != ArticleStatus.PENDING) {
            if (article.getStatus() == event.getToStatus()) {
                return;
            }
            throw BusinessException.conflict(
                    "Article status does not allow applying a review result: " + article.getStatus());
        }
        applyReviewDecision(article, event.getAction(), event.getAdminId(), event.getReason(), event);
    }

    private void applyReviewDecision(Article article,
                                     com.platform.enums.ReviewAction action,
                                     Long adminId,
                                     String reason,
                                     ReviewDecidedEvent event) {
        ArticleStatus fromStatus = article.getStatus();
        ArticleStatus toStatus = switch (action) {
            case APPROVE -> ArticleStatus.APPROVED;
            case RETURN -> ArticleStatus.RETURNED;
            case REJECT -> ArticleStatus.REJECTED;
            default -> throw BusinessException.badRequest("Unsupported review action");
        };

        article.setStatus(toStatus);
        if (action == com.platform.enums.ReviewAction.APPROVE) {
            article.setPublishedAt(event != null && event.getReviewedAt() != null
                    ? event.getReviewedAt()
                    : LocalDateTime.now());
        }
        articleMapper.updateById(article);
        eventOutboxService.saveEvent(
                "article",
                String.valueOf(article.getId()),
                EventConstants.ARTICLE_STATUS_CHANGED,
                buildStatusChangedEvent(article, fromStatus, toStatus)
        );
        log.info("Apply review decision: articleId={}, adminId={}, action={}, toStatus={}",
                article.getId(), adminId, action, toStatus);
    }

    private ArticleStatusChangedEvent buildStatusChangedEvent(Article article,
                                                              ArticleStatus fromStatus,
                                                              ArticleStatus toStatus) {
        return ArticleStatusChangedEvent.builder()
                .eventId(newEventId("article-status", article.getId()))
                .traceId(TraceContextHolder.get())
                .articleId(article.getId())
                .authorId(article.getAuthorId())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .title(article.getTitle())
                .summary(article.getSummary())
                .coverUrl(article.getCoverUrl())
                .coverColor(article.getCoverColor())
                .readMinutes(article.getReadMinutes())
                .durationCategory(article.getDurationCategory())
                .publishedAt(article.getPublishedAt())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String newEventId(String prefix, Long articleId) {
        return prefix + ":" + articleId + ":" + UUID.randomUUID();
    }

    private UserProfileArticleStatsDto buildProfileStats(Long authorId, boolean canViewAll) {
        long approved = countByStatus(authorId, ArticleStatus.APPROVED);
        int totalWordCount = articleMapper.selectList(
                        new LambdaQueryWrapper<Article>()
                                .eq(Article::getAuthorId, authorId)
                                .eq(Article::getStatus, ArticleStatus.APPROVED)
                                .select(Article::getWordCount))
                .stream()
                .mapToInt(article -> article.getWordCount() != null ? article.getWordCount() : 0)
                .sum();

        if (!canViewAll) {
            return UserProfileArticleStatsDto.builder()
                    .approved(approved)
                    .totalWordCount(totalWordCount)
                    .build();
        }

        return UserProfileArticleStatsDto.builder()
                .approved(approved)
                .pending(countByStatus(authorId, ArticleStatus.PENDING))
                .returned(countByStatus(authorId, ArticleStatus.RETURNED))
                .rejected(countByStatus(authorId, ArticleStatus.REJECTED))
                .draft(countByStatus(authorId, ArticleStatus.DRAFT))
                .totalWordCount(totalWordCount)
                .build();
    }

    private long countByStatus(Long authorId, ArticleStatus status) {
        return articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, status));
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

        return articles.stream()
                .filter(article -> article.getStatus() == ArticleStatus.RETURNED
                        || article.getStatus() == ArticleStatus.REJECTED)
                .collect(Collectors.toMap(
                        Article::getId,
                        article -> getLatestReviewReason(article.getId())
                ));
    }

    private String buildDraftKey(Long userId, Long articleId) {
        return DRAFT_KEY_PREFIX + userId + ":" + articleId;
    }

    private UserSummaryDto fetchUser(Long userId) {
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(
                new BatchUserQueryReq(Collections.singletonList(userId))));
        return users.isEmpty() ? null : users.get(0);
    }
}
