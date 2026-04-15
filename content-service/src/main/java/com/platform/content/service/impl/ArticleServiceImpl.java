package com.platform.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.review.dto.BatchLatestReviewReasonReq;
import com.platform.contract.review.dto.LatestReviewReasonDto;
import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.content.dto.UserProfileArticleItemDto;
import com.platform.contract.content.dto.UserProfileArticleStatsDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.context.TraceContextHolder;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.events.support.EventOutboxService;
import com.platform.content.api.resp.ArticleDetailResp;
import com.platform.content.api.resp.SubmitCooldownResp;
import com.platform.content.api.resp.SubmitResp;
import com.platform.content.entity.Article;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.exception.BusinessException;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.ArticleService;
import com.platform.content.util.ArticleUtils;
import com.platform.kernel.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文章服务实现，负责文章创建、提审、删除以及个人主页文章聚合等核心流程。
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
    private final AuthUserQueryClient authInternalClient;
    private final ReviewReasonClient reviewInternalClient;
    private final ReviewTaskClient reviewTaskInternalClient;
    private final EventOutboxService eventOutboxService;
    private final com.platform.content.service.HomeService homeService;

    /**
     * 创建一篇空草稿，返回文章主键和初始状态。
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
     * 查询文章详情。
     * 对可编辑文章优先返回 Redis 中的最新草稿内容，避免用户看到落后的数据库快照。
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
            // 可编辑文章优先读取 Redis 中尚未落库的最新草稿内容。
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
     * 提交文章进入审核流程。
     * 会校验文章状态、正文最小长度，以及 30 分钟的重复提审冷却时间。
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

        // 提审前再次以 Redis 中的最新草稿内容为准，避免提交旧版本正文。
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
     * 取消已发起但尚未处理的审核，把文章恢复为草稿状态。
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
        String eventId = newEventId("article-status", article.getId());
        ResultUtils.requireOk(reviewTaskInternalClient.removeTask(ReviewTaskRemoveReq.builder()
                .articleId(articleId)
                .lastEventId(eventId)
                .build()));
        eventOutboxService.saveEvent(
                "article",
                String.valueOf(articleId),
                EventConstants.ARTICLE_STATUS_CHANGED,
                buildStatusChangedEvent(article, fromStatus, ArticleStatus.DRAFT, eventId)
        );

        log.info("Cancel article review: articleId={}, userId={}", articleId, userId);
        return Map.of("status", ArticleStatus.DRAFT);
    }

    /**
     * 作者删除自己的文章，仅允许删除草稿、退回和拒绝状态的文章。
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
     * 相比作者侧删除，允许覆盖更广的状态范围。
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
     * 给审核服务提供文章快照，避免审核链路直接依赖内容库表结构。
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
     * 应用审核服务回调的审核结果。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyReviewResult(Long articleId, com.platform.contract.content.dto.ApplyReviewResultReq req) {
        Article article = getArticleByIdForWrite(articleId);
        if (article.getStatus() != ArticleStatus.PENDING) {
            throw BusinessException.conflict(
                    "Article status does not allow applying a review result: " + article.getStatus());
        }

        applyReviewDecision(article, req.getAction(), req.getAdminId(), req.getReason(), null);
    }

    /**
     * 查询个人主页文章列表，并根据访问者身份决定可见范围。
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
     * 校验当前用户是否可以读取指定文章。
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
     * 按 ID 查询文章；不存在时抛出 404。
     */
    private Article getArticleByIdForWrite(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }
        return article;
    }

    /**
     * 校验当前用户是否是文章作者。
     */
    private void checkOwnership(Article article, Long userId) {
        if (!userId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("Access denied");
        }
    }

    /**
     * 优先读取 Redis 中的最新草稿内容，缺失时回退到数据库正文。
     */
    private String getLatestContent(Long userId, Long articleId, String mysqlContent) {
        String redisContent = redisTemplate.opsForValue().get(buildDraftKey(userId, articleId));
        return redisContent != null ? redisContent : mysqlContent;
    }

    /**
     * 查询单篇文章最近一次退回或拒绝原因。
     * 仅用于 getArticleDetail 单篇详情场景；列表场景请使用 buildReviewReasonMap（批量接口）。
     */
    private String getLatestReviewReason(Long articleId) {
        LatestReviewReasonDto dto = ResultUtils.requireOk(reviewInternalClient.latestReason(articleId));
        return dto != null ? dto.getReason() : null;
    }

    /**
     * 消费审核结果事件，并把审核状态回写到文章。
     */
    @Transactional(rollbackFor = Exception.class)
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
     * 应用审核动作，并同步写入状态变更事件。
     */
    private void applyReviewDecision(Article article,
                                     com.platform.kernel.enums.ReviewAction action,
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
        if (action == com.platform.kernel.enums.ReviewAction.APPROVE) {
            article.setPublishedAt(event != null && event.getReviewedAt() != null
                    ? event.getReviewedAt()
                    : LocalDateTime.now());
            // 新文章通过审核，使首页 Hero 缓存失效，下次请求重新选取
            homeService.invalidateHeroCache();
        }
        articleMapper.updateById(article);
        eventOutboxService.saveEvent(
                "article",
                String.valueOf(article.getId()),
                EventConstants.ARTICLE_STATUS_CHANGED,
                buildStatusChangedEvent(article, fromStatus, toStatus, newEventId("article-status", article.getId()))
        );
        log.info("Apply review decision: articleId={}, adminId={}, action={}, toStatus={}",
                article.getId(), adminId, action, toStatus);
    }

    /**
     * 构造文章状态变更事件，供搜索、通知等派生服务消费。
     */
    private ArticleStatusChangedEvent buildStatusChangedEvent(Article article,
                                                              ArticleStatus fromStatus,
                                                              ArticleStatus toStatus,
                                                              String eventId) {
        return ArticleStatusChangedEvent.builder()
                .eventId(eventId)
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
     * 生成事件唯一标识。
     */
    private String newEventId(String prefix, Long articleId) {
        return prefix + ":" + articleId + ":" + UUID.randomUUID();
    }

    /**
     * 构造个人主页统计信息；非作者和非管理员只返回公开可见统计。
     * 使用单条 SQL 查询 status + wordCount 两列，内存聚合各状态计数和总字数，
     * 替代原来的 5×COUNT + 1×SELECT(wordCount) 共 6 次查询。
     */
    private UserProfileArticleStatsDto buildProfileStats(Long authorId, boolean canViewAll) {
        // MyBatis-Plus @TableLogic 会自动追加 deleted = 0，无需手动指定
        List<Article> allArticles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getAuthorId, authorId)
                        .select(Article::getStatus, Article::getWordCount));

        long approved = 0, pending = 0, returned = 0, rejected = 0, draft = 0;
        int totalWordCount = 0;
        for (Article a : allArticles) {
            totalWordCount += (a.getWordCount() != null ? a.getWordCount() : 0);
            switch (a.getStatus()) {
                case APPROVED -> approved++;
                case PENDING -> pending++;
                case RETURNED -> returned++;
                case REJECTED -> rejected++;
                case DRAFT -> draft++;
            }
        }

        if (!canViewAll) {
            return UserProfileArticleStatsDto.builder()
                    .approved(approved)
                    .totalWordCount(totalWordCount)
                    .build();
        }

        return UserProfileArticleStatsDto.builder()
                .approved(approved)
                .pending(pending)
                .returned(returned)
                .rejected(rejected)
                .draft(draft)
                .totalWordCount(totalWordCount)
                .build();
    }

    /**
     * 统计作者在指定状态下的文章数量。
     * 注意：buildProfileStats 已迁移至单次查询聚合，此方法暂保留供其他潜在调用方使用。
     */
    private long countByStatus(Long authorId, ArticleStatus status) {
        return articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, status));
    }

    /**
     * 根据主页 tab 参数解析筛选状态。
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
     * 只为退回和拒绝的文章补充审核原因映射。
     * 使用批量接口一次性查询，避免 N+1 远程调用。
     */
    private Map<Long, String> buildReviewReasonMap(List<Article> articles) {
        if (articles.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> needReasonIds = articles.stream()
                .filter(article -> article.getStatus() == ArticleStatus.RETURNED
                        || article.getStatus() == ArticleStatus.REJECTED)
                .map(Article::getId)
                .collect(Collectors.toList());

        if (needReasonIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<LatestReviewReasonDto> reasons = ResultUtils.requireOk(
                reviewInternalClient.batchLatestReasons(new BatchLatestReviewReasonReq(needReasonIds)));

        Map<Long, String> result = new HashMap<>();
        if (reasons != null) {
            for (LatestReviewReasonDto dto : reasons) {
                if (dto.getReason() != null) {
                    result.put(dto.getArticleId(), dto.getReason());
                }
            }
        }
        return result;
    }

    /**
     * 生成 Redis 草稿键。
     */
    private String buildDraftKey(Long userId, Long articleId) {
        return DRAFT_KEY_PREFIX + userId + ":" + articleId;
    }

    /**
     * 通过内部鉴权服务查询作者概要信息。
     */
    private UserSummaryDto fetchUser(Long userId) {
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(
                new BatchUserQueryReq(Collections.singletonList(userId))));
        return users.isEmpty() ? null : users.get(0);
    }
}



