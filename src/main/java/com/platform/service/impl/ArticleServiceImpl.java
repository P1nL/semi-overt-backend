package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.dto.resp.ArticleDetailResp;
import com.platform.dto.resp.SubmitCooldownResp;
import com.platform.dto.resp.SubmitResp;
import com.platform.entity.Article;
import com.platform.entity.ReviewLog;
import com.platform.entity.User;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import com.platform.exception.BusinessException;
import com.platform.mapper.ArticleMapper;
import com.platform.mapper.ReviewLogMapper;
import com.platform.mapper.UserMapper;
import com.platform.service.ArticleService;
import com.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

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
    private final ReviewLogMapper reviewLogMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

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
            throw BusinessException.notFound("文章不存在");
        }

        checkReadPermission(article, currentUserId);

        User author = userMapper.selectById(article.getAuthorId());
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
                    "当前状态不允许提交审核，状态为：" + article.getStatus());
        }

        String content = getLatestContent(userId, articleId, article.getContent());
        if (content == null || content.length() < MIN_CONTENT_LENGTH) {
            throw BusinessException.badRequest(
                    "正文内容不足 " + MIN_CONTENT_LENGTH + " 字，请补充后再提交");
        }

        if (article.getLastSubmittedAt() != null) {
            LocalDateTime cooldownEnd = article.getLastSubmittedAt()
                    .plusMinutes(SUBMIT_COOLDOWN_MINUTES);
            if (LocalDateTime.now().isBefore(cooldownEnd)) {
                long remainingSeconds = Math.max(1, ChronoUnit.SECONDS.between(LocalDateTime.now(), cooldownEnd));
                throw BusinessException.tooManyRequests(
                        "同一文章 30 分钟内只能提交一次审核，请稍后再试",
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
                    "只有待审核状态的文章才能取消审核，当前状态为：" + article.getStatus());
        }

        article.setStatus(ArticleStatus.DRAFT);
        articleMapper.updateById(article);

        writeReviewLog(articleId, userId,
                ReviewAction.CANCEL,
                ArticleStatus.PENDING,
                ArticleStatus.DRAFT,
                null);

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
                    "当前状态不允许删除，状态为：" + article.getStatus()
                            + "，仅 DRAFT / RETURNED / REJECTED 状态可删除");
        }

        articleMapper.deleteById(articleId);
        redisTemplate.delete(buildDraftKey(userId, articleId));

        log.info("Delete article: articleId={}, userId={}", articleId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> adminDeleteArticle(Long articleId, Long adminId) {
        if (!SecurityUtils.isAdmin()) {
            throw BusinessException.forbidden("无权执行管理员删除操作");
        }

        Article article = getArticleByIdForWrite(articleId);

        if (!ADMIN_DELETABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict("当前状态不允许管理员删除，状态为：" + article.getStatus());
        }

        articleMapper.deleteById(articleId);
        redisTemplate.delete(buildDraftKey(article.getAuthorId(), articleId));

        log.info("Admin delete article: articleId={}, adminId={}, authorId={}, status={}",
                articleId, adminId, article.getAuthorId(), article.getStatus());

        return Map.of("ok", true);
    }

    private void checkReadPermission(Article article, Long currentUserId) {
        if (article.getStatus() == ArticleStatus.APPROVED) {
            return;
        }

        boolean isAuthor = currentUserId != null
                && currentUserId.equals(article.getAuthorId());

        if (article.getStatus() == ArticleStatus.PENDING) {
            boolean isAdmin = SecurityUtils.isAdmin();
            if (!isAuthor && !isAdmin) {
                throw BusinessException.notFound("文章不存在");
            }
            return;
        }

        if (!isAuthor) {
            throw BusinessException.notFound("文章不存在");
        }
    }

    private void checkOwnership(Article article, Long userId) {
        if (!userId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("无权操作他人的文章");
        }
    }

    private Article getArticleByIdForWrite(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("文章不存在");
        }
        return article;
    }

    private String getLatestContent(Long userId, Long articleId, String mysqlContent) {
        String redisContent = redisTemplate.opsForValue().get(buildDraftKey(userId, articleId));
        return redisContent != null ? redisContent : mysqlContent;
    }

    private String getLatestReviewReason(Long articleId) {
        ReviewLog log = reviewLogMapper.selectOne(
                new LambdaQueryWrapper<ReviewLog>()
                        .eq(ReviewLog::getArticleId, articleId)
                        .in(ReviewLog::getAction, ReviewAction.RETURN, ReviewAction.REJECT)
                        .orderByDesc(ReviewLog::getCreatedAt)
                        .last("LIMIT 1")
        );
        return log != null ? log.getReason() : null;
    }

    private void writeReviewLog(Long articleId, Long operatorId,
                                ReviewAction action,
                                ArticleStatus fromStatus,
                                ArticleStatus toStatus,
                                String reason) {
        ReviewLog log = new ReviewLog();
        log.setArticleId(articleId);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setReason(reason);
        reviewLogMapper.insert(log);
    }

    private String buildDraftKey(Long userId, Long articleId) {
        return DRAFT_KEY_PREFIX + userId + ":" + articleId;
    }
}
