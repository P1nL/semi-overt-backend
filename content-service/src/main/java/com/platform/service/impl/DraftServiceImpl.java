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
 * 草稿服务实现
 *
 * 草稿存储策略：
 *   - 正文（content）存 Redis，Key: draft:{userId}:{articleId}，TTL 7 天
 *   - 元数据（title/summary/coverUrl/wordCount 等）同步写 MySQL
 *   - 定时任务每 5 分钟将 Redis 内容刷盘到 MySQL content 字段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftServiceImpl implements DraftService {

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ReviewInternalClient reviewInternalClient;

    /** 草稿 Redis Key 前缀 */
    private static final String DRAFT_KEY_PREFIX = "draft:";

    /** 草稿 Redis TTL（天） */
    private static final long DRAFT_TTL_DAYS = 7;

    /** 摘要默认截取长度 */

    /**
     * 允许自动保存的文章状态
     * PENDING / APPROVED / REJECTED 状态下正文已锁定，不允许保存
     */
    private static final Set<ArticleStatus> EDITABLE_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.RETURNED);

    // ==================== 自动保存草稿 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaveDraftResp saveDraft(Long articleId, Long userId, SaveDraftReq req) {
        // 1. 查文章，校验所有权和状态
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

        // 2. 正文写入 Redis（续期 TTL）
        if (req.getContent() != null) {
            String redisKey = buildDraftKey(userId, articleId);
            redisTemplate.opsForValue().set(
                    redisKey,
                    req.getContent(),
                    DRAFT_TTL_DAYS,
                    TimeUnit.DAYS
            );
        }

        // 3. 计算字数和阅读时长（以最新正文为准）
        String latestContent = req.getContent() != null
                ? req.getContent()
                : article.getContent();
        int wordCount = calculateWordCount(latestContent);
        BigDecimal readMinutes = calculateReadMinutes(wordCount);
        DurationCategory durationCategory = getDurationCategory(readMinutes);

        // 4. 摘要：前端传了就用，没传且正文有内容则自动截取
        // 5. 更新 MySQL 元数据（仅更新非 null 字段）
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

    // ==================== 草稿箱列表 ====================

    @Override
    public List<DraftItemResp> getDraftList(Long userId) {
        // 1. 查 DRAFT + RETURNED 文章，按更新时间倒序
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getAuthorId, userId)
                        .in(Article::getStatus, ArticleStatus.DRAFT, ArticleStatus.RETURNED)
                        .orderByDesc(Article::getUpdatedAt)
        );

        if (articles.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 批量查询 RETURNED 文章的最近退回原因
        //    先找出所有 RETURNED 文章 ID，一次性查 review_logs
        List<Long> returnedIds = articles.stream()
                .filter(a -> a.getStatus() == ArticleStatus.RETURNED)
                .map(Article::getId)
                .collect(Collectors.toList());

        // articleId → latestReason 的映射
        java.util.Map<Long, String> reasonMap = new java.util.HashMap<>();
        if (!returnedIds.isEmpty()) {
            for (Long artId : returnedIds) {
                LatestReviewReasonDto dto = ResultUtils.requireOk(reviewInternalClient.latestReason(artId));
                if (dto != null && dto.getAction() == ReviewAction.RETURN) {
                    reasonMap.put(artId, dto.getReason());
                }
            }
        }

        // 3. 组装响应
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

    // ==================== 定时刷盘 ====================

    @Override
    public void flushAllDrafts() {
        // SCAN 所有草稿 Key，格式：draft:{userId}:{articleId}
        Set<String> keys = redisTemplate.keys(DRAFT_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.debug("草稿刷盘：无待处理的草稿 Key");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (String key : keys) {
            try {
                // 解析 Key：draft:{userId}:{articleId}
                String[] parts = key.split(":");
                if (parts.length != 3) {
                    log.warn("草稿 Key 格式异常，跳过: key={}", key);
                    continue;
                }

                Long articleId = Long.parseLong(parts[2]);
                String content = redisTemplate.opsForValue().get(key);
                if (content == null) {
                    continue; // Key 在读取过程中过期，跳过
                }

                // 只更新 content 字段，避免覆盖其他元数据
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

    // ==================== 私有工具方法 ====================

    /**
     * 计算字数（字符数）
     * 对中文内容而言，字符数比"词数"更有意义
     * 去掉 Markdown 语法符号后计算（简单方案：直接计全文长度）
     */
    private int calculateWordCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        // 去除 Markdown 常见语法符号（#、*、`、> 等）和空白，取纯文本长度
        String plain = content
                .replaceAll("```[\\s\\S]*?```", "")   // 代码块
                .replaceAll("`[^`]+`", "")             // 行内代码
                .replaceAll("[#*>\\-_\\[\\]()!|]", "") // Markdown 符号
                .replaceAll("https?://\\S+", "")       // URL
                .replaceAll("\\s+", "");               // 空白字符
        return plain.length();
    }

    /**
     * 计算阅读时长（分钟）
     * 按 wordCount / 300 计算，保留一位小数
     */
    private BigDecimal calculateReadMinutes(int wordCount) {
        if (wordCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(wordCount)
                .divide(BigDecimal.valueOf(300), 1, RoundingMode.HALF_UP);
    }

    /**
     * 根据阅读时长确定分类
     * QUICK  ≤ 3 分钟（≤ 900 字）
     * SHORT  ≤ 8 分钟（≤ 2400 字）
     * DEEP   > 8 分钟（> 2400 字）
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
     * 截取摘要：取正文前 120 个字符，去掉 Markdown 语法符号
     */
    /**
     * 构建草稿 Redis Key：draft:{userId}:{articleId}
     */
    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildDraftKey(Long userId, Long articleId) {
        return DRAFT_KEY_PREFIX + userId + ":" + articleId;
    }
}
