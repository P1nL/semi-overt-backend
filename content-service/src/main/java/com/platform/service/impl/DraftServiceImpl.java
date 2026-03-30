package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.platform.client.ReviewInternalClient;
import com.platform.common.api.ResultUtils;
import com.platform.common.dto.internal.LatestReviewReasonDto;
import com.platform.dto.req.SaveDraftReq;
import com.platform.dto.resp.DraftItemResp;
import com.platform.dto.resp.SaveDraftResp;
import com.platform.entity.Article;
import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import com.platform.enums.ReviewAction;
import com.platform.exception.BusinessException;
import com.platform.mapper.ArticleMapper;
import com.platform.service.DraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 草稿服务实现。
 * 负责自动保存草稿、查询草稿箱以及把 Redis 中的正文内容回刷到 MySQL。
 * 内容正文以 Redis 暂存为主，文章元数据则实时写入 MySQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftServiceImpl implements DraftService {

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ReviewInternalClient reviewInternalClient;

    /** 草稿正文在 Redis 中的 key 前缀。 */
    private static final String DRAFT_KEY_PREFIX = "draft:";

    /** 草稿正文在 Redis 中的保留天数。 */
    private static final long DRAFT_TTL_DAYS = 7;

    /** 允许自动保存的文章状态。 */
    private static final Set<ArticleStatus> EDITABLE_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.RETURNED);

    /**
     * 自动保存草稿。
     * 正文优先写入 Redis 以支持高频保存，字数、阅读时长、封面等元数据同步落 MySQL。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaveDraftResp saveDraft(Long articleId, Long userId, SaveDraftReq req) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("文章不存在");
        }
        if (!userId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("无权编辑他人的文章");
        }
        if (!EDITABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict(
                    "当前状态不允许保存草稿，状态：" + article.getStatus());
        }

        if (req.getContent() != null) {
            String redisKey = buildDraftKey(userId, articleId);
            redisTemplate.opsForValue().set(
                    redisKey,
                    req.getContent(),
                    DRAFT_TTL_DAYS,
                    TimeUnit.DAYS
            );
        }

        String latestContent = req.getContent() != null
                ? req.getContent()
                : article.getContent();
        int wordCount = calculateWordCount(latestContent);
        BigDecimal readMinutes = calculateReadMinutes(wordCount);
        DurationCategory durationCategory = getDurationCategory(readMinutes);

        LambdaUpdateWrapper<Article> updateWrapper = new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .set(Article::getWordCount, wordCount)
                .set(Article::getReadMinutes, readMinutes)
                .set(Article::getDurationCategory, durationCategory);

        if (req.getContent() != null) {
            updateWrapper.set(Article::getContent, req.getContent());
        }

        if (req.getSummary() != null) {
            updateWrapper.set(Article::getSummary, normalizeNullableText(req.getSummary()));
        }

        if (req.getTitle() != null) {
            updateWrapper.set(Article::getTitle, normalizeNullableText(req.getTitle()));
        }
        if (req.getCoverUrl() != null) {
            updateWrapper.set(Article::getCoverUrl, normalizeNullableText(req.getCoverUrl()));
        }
        if (req.getCoverColor() != null) {
            updateWrapper.set(Article::getCoverColor, normalizeNullableText(req.getCoverColor()));
        }

        articleMapper.update(null, updateWrapper);

        LocalDateTime savedAt = LocalDateTime.now();
        log.debug("草稿自动保存: articleId={}, userId={}, wordCount={}", articleId, userId, wordCount);

        return SaveDraftResp.builder()
                .savedAt(savedAt)
                .wordCount(wordCount)
                .readMinutes(readMinutes)
                .durationCategory(durationCategory)
                .status(article.getStatus())
                .build();
    }


    /**
     * 查询草稿箱列表。
     * 仅返回作者本人处于 DRAFT 或 RETURNED 状态的文章，并补充最近一次退回原因。
     */
    @Override
    public List<DraftItemResp> getDraftList(Long userId) {
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getAuthorId, userId)
                        .in(Article::getStatus, ArticleStatus.DRAFT, ArticleStatus.RETURNED)
                        .orderByDesc(Article::getUpdatedAt)
        );

        if (articles.isEmpty()) {
            return new ArrayList<>();
        }

        // 退回原因来自 review-service，当前实现按文章逐个查询最近一条 RETURN 记录。
        List<Long> returnedIds = articles.stream()
                .filter(a -> a.getStatus() == ArticleStatus.RETURNED)
                .map(Article::getId)
                .collect(Collectors.toList());

        java.util.Map<Long, String> reasonMap = new java.util.HashMap<>();
        if (!returnedIds.isEmpty()) {
            for (Long artId : returnedIds) {
                LatestReviewReasonDto dto = ResultUtils.requireOk(reviewInternalClient.latestReason(artId));
                if (dto != null && dto.getAction() == ReviewAction.RETURN) {
                    reasonMap.put(artId, dto.getReason());
                }
            }
        }

        return articles.stream()
                .map(a -> DraftItemResp.builder()
                        .id(a.getId())
                        .title(a.getTitle())
                        .status(a.getStatus())
                        .wordCount(a.getWordCount())
                        .updatedAt(a.getUpdatedAt())
                        .latestReason(reasonMap.get(a.getId()))
                        .build())
                .collect(Collectors.toList());
    }


    /**
     * 定时把 Redis 中的草稿正文回刷到 MySQL。
     * 只同步 content 字段，避免覆盖其他已在数据库中更新的元数据。
     */
    @Override
    public void flushAllDrafts() {
        Set<String> keys = redisTemplate.keys(DRAFT_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.debug("草稿刷盘：无待处理的草稿 Key");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (String key : keys) {
            try {
                // key 结构固定为 draft:{userId}:{articleId}，只使用 articleId 回写正文。
                String[] parts = key.split(":");
                if (parts.length != 3) {
                    log.warn("草稿 Key 格式异常，跳过: key={}", key);
                    continue;
                }

                Long articleId = Long.parseLong(parts[2]);
                String content = redisTemplate.opsForValue().get(key);
                if (content == null) {
                    continue;
                }

                Article update = new Article();
                update.setId(articleId);
                update.setContent(content);
                articleMapper.updateById(update);

                successCount++;
            } catch (NumberFormatException e) {
                log.warn("草稿 Key 解析失败，跳过: key={}", key);
                failCount++;
            } catch (Exception e) {
                log.error("草稿刷盘失败: key={}, error={}", key, e.getMessage(), e);
                failCount++;
            }
        }

        log.info("草稿刷盘完成: 成功={}, 失败={}, 总计={}", successCount, failCount, keys.size());
    }


    /**
     * 计算正文“有效字符数”。
     * 这里会粗略剔除 Markdown 语法、代码块、URL 和空白字符，使字数更接近实际阅读内容。
     */
    private int calculateWordCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String plain = content
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("`[^`]+`", "")
                .replaceAll("[#*>\\-_\\[\\]()!|]", "")
                .replaceAll("https?://\\S+", "")
                .replaceAll("\\s+", "");
        return plain.length();
    }

    /**
     * 以 300 字/分钟估算阅读时长，并保留一位小数。
     */
    private BigDecimal calculateReadMinutes(int wordCount) {
        if (wordCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(wordCount)
                .divide(BigDecimal.valueOf(300), 1, RoundingMode.HALF_UP);
    }

    /**
     * 根据阅读时长映射文章阅读分类。
     */
    private DurationCategory getDurationCategory(BigDecimal readMinutes) {
        double minutes = readMinutes.doubleValue();
        if (minutes <= 3.0) {
            return DurationCategory.QUICK;
        } else if (minutes <= 8.0) {
            return DurationCategory.SHORT;
        } else {
            return DurationCategory.DEEP;
        }
    }

    /**
     * 统一处理可为空的文本字段。
     * 空串会被折叠为 null，避免数据库中混入无意义空白值。
     */
    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 构造草稿正文在 Redis 中的 key。
     */
    private String buildDraftKey(Long userId, Long articleId) {
        return DRAFT_KEY_PREFIX + userId + ":" + articleId;
    }
}
