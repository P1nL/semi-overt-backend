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

/**
 * 文章主流程服务实现。
 * 负责文章创建、详情访问、提审、撤回审核、删除、审核结果落库以及用户主页文章聚合。
 * 该类同时维护内容域的状态机规则、权限判断和向下游发出的 outbox 事件。
 */
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

    /**
     * 创建一篇空白文章。
     * 新文章默认进入 DRAFT 状态，后续正文由草稿服务负责高频保存。
     */
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

    /**
     * 获取文章详情。
     * 会先做状态级权限判断；对于草稿和退回文章，正文优先读取 Redis 中尚未提交的最新内容。
     */
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
            // 未提交的最新正文可能仍停留在 Redis 草稿中，详情页优先展示最新编辑结果。
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

    /**
     * 提交文章进入审核。
     * 先校验作者身份、状态、正文长度与提交冷却时间，再把正文从 Redis 刷入实体并发送 ARTICLE_SUBMITTED 事件。
     */
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
            // 单篇文章 30 分钟内只允许提交一次，避免重复刷审核队列。
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

        // 提审前以 Redis 中的最新草稿正文为准，并在提交成功后删除草稿缓存。
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

    /**
     * 撤回审核。
     * 仅允许作者对 PENDING 文章操作，并通过 ARTICLE_STATUS_CHANGED 事件通知审核投影删除任务。
     */
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

    /**
     * 作者删除文章。
     * 只允许删除仍可编辑或已终止的状态，同时清理 Redis 中残留的草稿正文。
     */
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

    /**
     * 管理员删除文章。
     * 与作者删除相比，允许覆盖更广的状态集合，包括待审和已发布文章。
     */
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

    /**
     * 向 review-service 暴露文章审核快照。
     * 返回审核所需的标题、摘要、正文、状态和提审次数等关键信息。
     */
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

    /**
     * 处理 review-service 的同步审核结果回写。
     * 仅当文章仍处于 PENDING 时允许落最终状态，防止覆盖作者已撤回的文章。
     */
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

    /**
     * 查询用户主页文章列表。
     * 是否能查看全部状态由当前访问者是否为作者本人或管理员决定。
     */
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

    /**
     * 文章详情访问控制。
     * APPROVED 全员可见；PENDING 仅作者和管理员可见；其余状态仅作者本人可见。
     * 对无权访问者统一返回 404，避免暴露资源存在性。
     */
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

    /**
     * 校验文章作者身份。
     */
    private void checkOwnership(Article article, Long userId) {
        if (!userId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("Access denied");
        }
    }

    /**
     * 读取文章并确保其存在。
     */
    private Article getArticleByIdForWrite(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }
        return article;
    }

    /**
     * 获取当前最新正文。
     * 若 Redis 中存在未落库草稿，则优先使用 Redis 内容，否则退回 MySQL 正文。
     */
    private String getLatestContent(Long userId, Long articleId, String mysqlContent) {
        String redisContent = redisTemplate.opsForValue().get(buildDraftKey(userId, articleId));
        return redisContent != null ? redisContent : mysqlContent;
    }

    /**
     * 查询最近一次审核原因。
     */
    private String getLatestReviewReason(Long articleId) {
        LatestReviewReasonDto dto = ResultUtils.requireOk(reviewInternalClient.latestReason(articleId));
        return dto != null ? dto.getReason() : null;
    }

    /**
     * 处理 REVIEW_DECIDED 事件。
     * 若事件已被重复消费且文章状态已经是目标状态，则直接视为幂等成功。
     */
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

    /**
     * 应用审核动作并发送 ARTICLE_STATUS_CHANGED 事件。
     * APPROVE 会补齐发布时间，RETURN/REJECT 则仅更新状态和审核理由相关视图。
     */
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

    /**
     * 组装文章状态变更事件。
     * 该事件供审核、搜索、通知等派生服务更新各自投影。
     */
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

    /**
     * 构造带业务前缀的事件 ID，便于跨服务日志追踪。
     */
    private String newEventId(String prefix, Long articleId) {
        return prefix + ":" + articleId + ":" + UUID.randomUUID();
    }

    /**
     * 统计用户主页需要展示的文章数量指标。
     * 非作者和非管理员只能看到公开统计，因此仅返回已发布数量和总字数。
     */
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

    /**
     * 按状态统计文章数量。
     */
    private long countByStatus(Long authorId, ArticleStatus status) {
        return articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, status));
    }

    /**
     * 根据用户主页 tab 解析文章状态过滤条件。
     * 公开访问只能查看 APPROVED，因此忽略外部传入的其他状态。
     */
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

    /**
     * 为退回或拒绝的文章补齐最近一次审核原因。
     */
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

    /**
     * 构造文章草稿在 Redis 中的 key。
     */
    private String buildDraftKey(Long userId, Long articleId) {
        return DRAFT_KEY_PREFIX + userId + ":" + articleId;
    }

    /**
     * 调用 auth-service 查询作者摘要信息。
     */
    private UserSummaryDto fetchUser(Long userId) {
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(
                new BatchUserQueryReq(Collections.singletonList(userId))));
        return users.isEmpty() ? null : users.get(0);
    }
}
