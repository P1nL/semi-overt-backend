package com.platform.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.dto.BatchLatestReviewReasonReq;
import com.platform.contract.review.dto.LatestReviewReasonDto;
import com.platform.content.api.req.SaveDraftReq;
import com.platform.content.api.resp.DraftItemResp;
import com.platform.content.api.resp.SaveDraftResp;
import com.platform.content.entity.Article;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.exception.BusinessException;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.DraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftServiceImpl implements DraftService {

    private static final String DRAFT_KEY_PREFIX = "draft:";
    private static final long DRAFT_TTL_DAYS = 7;
    private static final Set<ArticleStatus> EDITABLE_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.RETURNED);

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ReviewReasonClient reviewInternalClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaveDraftResp saveDraft(Long articleId, Long userId, SaveDraftReq req) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }
        if (!userId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("Cannot edit another user's article");
        }
        if (!EDITABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict("Current status does not allow saving drafts: " + article.getStatus());
        }

        if (req.getContent() != null) {
            redisTemplate.opsForValue().set(
                    buildDraftKey(userId, articleId),
                    req.getContent(),
                    DRAFT_TTL_DAYS,
                    TimeUnit.DAYS
            );
        }

        String latestContent = req.getContent() != null ? req.getContent() : article.getContent();
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

        return SaveDraftResp.builder()
                .savedAt(LocalDateTime.now())
                .wordCount(wordCount)
                .readMinutes(readMinutes)
                .durationCategory(durationCategory)
                .status(article.getStatus())
                .build();
    }

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

        List<Long> returnedIds = articles.stream()
                .filter(a -> a.getStatus() == ArticleStatus.RETURNED)
                .map(Article::getId)
                .collect(Collectors.toList());

        Map<Long, String> reasonMap = new HashMap<>();
        if (!returnedIds.isEmpty()) {
            List<LatestReviewReasonDto> reasons = ResultUtils.requireOk(
                    reviewInternalClient.batchLatestReasons(new BatchLatestReviewReasonReq(returnedIds)));
            if (reasons != null) {
                for (LatestReviewReasonDto dto : reasons) {
                    if (dto.getAction() == ReviewAction.RETURN) {
                        reasonMap.put(dto.getArticleId(), dto.getReason());
                    }
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
                .toList();
    }

    @Override
    public void flushAllDrafts() {
        Set<String> keys = redisTemplate.keys(DRAFT_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            log.debug("No drafts to flush");
            return;
        }

        int successCount = 0;
        int failCount = 0;
        for (String key : keys) {
            try {
                String[] parts = key.split(":");
                if (parts.length != 3) {
                    log.warn("Skip invalid draft key: {}", key);
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
                log.warn("Skip unparsable draft key: {}", key);
                failCount++;
            } catch (Exception e) {
                log.error("Failed to flush draft: key={}, error={}", key, e.getMessage(), e);
                failCount++;
            }
        }

        log.info("Draft flush completed: success={}, failed={}, total={}", successCount, failCount, keys.size());
    }

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

    private BigDecimal calculateReadMinutes(int wordCount) {
        if (wordCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(wordCount)
                .divide(BigDecimal.valueOf(300), 1, RoundingMode.HALF_UP);
    }

    private DurationCategory getDurationCategory(BigDecimal readMinutes) {
        double minutes = readMinutes.doubleValue();
        if (minutes <= 3.0) {
            return DurationCategory.QUICK;
        }
        if (minutes <= 8.0) {
            return DurationCategory.SHORT;
        }
        return DurationCategory.DEEP;
    }

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
