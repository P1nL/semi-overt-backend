package com.platform.content.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.content.api.resp.ArticleCardResp;
import com.platform.content.api.resp.HomeResp;
import com.platform.content.entity.Article;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.HomeService;
import com.platform.content.util.ArticleUtils;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.enums.DurationCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private static final int CARD_PREVIEW_MAX_LENGTH = 120;
    private static final int HERO_TOTAL = 11;
    private static final int SECTION_LIMIT = 6;
    private static final String HERO_CACHE_PREFIX = "home:hero:v";

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthUserQueryClient authInternalClient;

    @Override
    public HomeResp getHomeData() {
        List<Article> heroArticles = getOrCacheHeroArticles();

        Article primary = heroArticles.isEmpty() ? null : heroArticles.get(0);
        List<Article> secondary = heroArticles.size() > 1
                ? heroArticles.subList(1, heroArticles.size())
                : Collections.emptyList();

        List<Article> quickList = articleMapper.selectApprovedByCategory(DurationCategory.QUICK, SECTION_LIMIT);
        List<Article> shortList = articleMapper.selectApprovedByCategory(DurationCategory.SHORT, SECTION_LIMIT);
        List<Article> deepList = articleMapper.selectApprovedByCategory(DurationCategory.DEEP, SECTION_LIMIT);

        Set<Long> authorIds = new HashSet<>();
        if (primary != null) {
            authorIds.add(primary.getAuthorId());
        }
        secondary.forEach(a -> authorIds.add(a.getAuthorId()));
        quickList.forEach(a -> authorIds.add(a.getAuthorId()));
        shortList.forEach(a -> authorIds.add(a.getAuthorId()));
        deepList.forEach(a -> authorIds.add(a.getAuthorId()));

        Map<Long, UserSummaryDto> userMap = batchFetchUsers(authorIds);

        HomeResp.HeroData hero = HomeResp.HeroData.builder()
                .primary(primary != null ? toCard(primary, userMap) : null)
                .secondary(toCards(secondary, userMap))
                .build();

        List<HomeResp.SectionData> sections = List.of(
                HomeResp.SectionData.builder().category(DurationCategory.QUICK).list(toCards(quickList, userMap)).build(),
                HomeResp.SectionData.builder().category(DurationCategory.SHORT).list(toCards(shortList, userMap)).build(),
                HomeResp.SectionData.builder().category(DurationCategory.DEEP).list(toCards(deepList, userMap)).build()
        );

        return HomeResp.builder().hero(hero).sections(sections).build();
    }

    @Override
    public void invalidateHeroCache() {
        String cacheKey = HERO_CACHE_PREFIX + HERO_TOTAL + ":" + LocalDate.now();
        try {
            redisTemplate.delete(cacheKey);
            log.info("Hero 缓存已失效（新文章通过审核）: key={}", cacheKey);
        } catch (Exception e) {
            log.warn("清除 Hero 缓存失败: {}", e.getMessage());
        }
    }

    private List<Article> getOrCacheHeroArticles() {
        String cacheKey = HERO_CACHE_PREFIX + HERO_TOTAL + ":" + LocalDate.now();

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<Long> ids = objectMapper.readValue(cached, new TypeReference<List<Long>>() {});
                if (!ids.isEmpty()) {
                    List<Article> articles = fetchArticlesByIds(ids);
                    if (!articles.isEmpty()) {
                        return articles;
                    }
                    log.warn("Hero 缓存引用了不存在的文章，已删除过期缓存");
                    redisTemplate.delete(cacheKey);
                }
            }
        } catch (Exception e) {
            log.warn("读取 Hero 缓存失败，回退到轮替查询: {}", e.getMessage());
            return selectHeroArticlesFair(HERO_TOTAL);
        }

        List<Article> articles = selectHeroArticlesFair(HERO_TOTAL);
        if (articles.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Long> ids = articles.stream().map(Article::getId).collect(Collectors.toList());
            String json = objectMapper.writeValueAsString(ids);
            Duration ttl = Duration.between(LocalDateTime.now(), LocalDate.now().atTime(LocalTime.MAX));
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
            log.info("Hero 缓存已刷新: key={}, ids={}", cacheKey, ids);
        } catch (Exception e) {
            log.warn("写入 Hero 缓存失败: {}", e.getMessage());
        }

        return articles;
    }

    /**
     * 公平轮替选取首页文章：
     * 1. 优先从未曝光（last_featured_at IS NULL）的文章中随机取 limit 条
     * 2. 若不足，从已曝光的文章中随机补充
     * 3. 若所有文章都已曝光（已补充完），先重置标记再重新取
     * 4. 成功选取后批量标记为"已曝光"
     */
    private List<Article> selectHeroArticlesFair(int limit) {
        List<Article> unfeatured = articleMapper.selectUnfeaturedApproved(limit);

        List<Article> result;
        if (unfeatured.size() >= limit) {
            // 场景1：未曝光文章充足
            result = unfeatured;
        } else if (!unfeatured.isEmpty()) {
            // 场景2：未曝光文章不足，从已曝光文章中补充
            int needed = limit - unfeatured.size();
            List<Article> featured = articleMapper.selectFeaturedApproved(needed);
            result = new java.util.ArrayList<>(unfeatured);
            result.addAll(featured);
            // 随机打乱保证顺序不总是"未曝光在前"
            Collections.shuffle(result);
        } else {
            // 场景3：全部文章都已曝光，清空标记后重新轮替
            articleMapper.resetAllFeatured();
            log.info("Hero 轮替：所有文章已曝光，重置 last_featured_at 开始新一轮");
            result = articleMapper.selectUnfeaturedApproved(limit);
            if (result.isEmpty()) {
                // 降级：直接随机（文章数量极少时兜底）
                return articleMapper.selectRandomApproved(limit);
            }
        }

        // 批量标记选中的文章为"已曝光"
        if (!result.isEmpty()) {
            List<Long> ids = result.stream().map(Article::getId).collect(Collectors.toList());
            try {
                articleMapper.markAsFeatured(ids);
            } catch (Exception e) {
                log.warn("批量更新 last_featured_at 失败（不影响展示）: {}", e.getMessage());
            }
        }

        return result;
    }

    private List<Article> fetchArticlesByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> articles = articleMapper.selectBatchIds(ids);

        Map<Long, Article> articleMap = articles.stream()
                .filter(a -> a.getDeleted() == 0)
                .collect(Collectors.toMap(Article::getId, a -> a));

        return ids.stream()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        BatchUserQueryReq req = new BatchUserQueryReq();
        req.setUserIds(authorIds.stream().toList());
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(req));
        return users.stream().collect(Collectors.toMap(UserSummaryDto::getId, u -> u));
    }

    private ArticleCardResp toCard(Article article, Map<Long, UserSummaryDto> userMap) {
        UserSummaryDto author = userMap.get(article.getAuthorId());
        return ArticleCardResp.builder()
                .articleId(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .previewText(ArticleUtils.extractPreviewText(article.getContent(), CARD_PREVIEW_MAX_LENGTH))
                .coverUrl(article.getCoverUrl())
                .coverColor(article.getCoverColor())
                .readMinutes(article.getReadMinutes())
                .durationCategory(article.getDurationCategory())
                .status(article.getStatus())
                .authorId(author != null ? author.getId() : null)
                .authorName(author != null ? author.getNickname() : null)
                .authorAvatar(author != null ? author.getAvatarUrl() : null)
                .publishedAt(article.getPublishedAt())
                .updatedAt(article.getUpdatedAt())
                .rejectReason(null)
                .build();
    }

    private List<ArticleCardResp> toCards(List<Article> articles, Map<Long, UserSummaryDto> userMap) {
        return articles.stream().map(a -> toCard(a, userMap)).collect(Collectors.toList());
    }
}
