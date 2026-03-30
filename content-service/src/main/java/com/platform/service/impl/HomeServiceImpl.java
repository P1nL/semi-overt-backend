package com.platform.service.impl;

import com.platform.client.AuthInternalClient;
import com.platform.common.api.ResultUtils;
import com.platform.common.dto.internal.BatchUserQueryReq;
import com.platform.common.dto.internal.UserSummaryDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.resp.ArticleCardResp;
import com.platform.dto.resp.HomeResp;
import com.platform.entity.Article;
import com.platform.enums.DurationCategory;
import com.platform.mapper.ArticleMapper;
import com.platform.service.HomeService;
import com.platform.util.ArticleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页服务实现。
 * 负责组装 Hero 区域与按阅读时长分类的首页 section，并补齐作者摘要信息。
 * Hero 区域使用按天缓存的随机文章集合，保证同一天内对所有访问者展示一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private static final int CARD_PREVIEW_MAX_LENGTH = 120;

    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthInternalClient authInternalClient;

    /** Hero 区域展示的文章总数，包含 1 个主卡和 4 个次卡。 */
    private static final int HERO_TOTAL = 5;

    /** 每个首页 section 返回的文章条数。 */
    private static final int SECTION_LIMIT = 6;

    /** 首页 Hero 缓存 key 前缀，完整 key 形如 home:hero:yyyy-MM-dd。 */
    private static final String HERO_CACHE_PREFIX = "home:hero:";

    /**
     * 组装首页数据。
     * 包括每日随机 Hero、按阅读时长分类的 section，以及统一的作者摘要信息。
     */
    @Override
    public HomeResp getHomeData() {
        List<Article> heroArticles = getOrCacheHeroArticles();

        Article primary = heroArticles.isEmpty() ? null : heroArticles.get(0);
        List<Article> secondary = heroArticles.size() > 1
                ? heroArticles.subList(1, heroArticles.size())
                : Collections.emptyList();

        List<Article> quickList = articleMapper.selectApprovedByCategory(DurationCategory.QUICK,  SECTION_LIMIT);
        List<Article> shortList = articleMapper.selectApprovedByCategory(DurationCategory.SHORT,  SECTION_LIMIT);
        List<Article> deepList  = articleMapper.selectApprovedByCategory(DurationCategory.DEEP,   SECTION_LIMIT);

        Set<Long> authorIds = new HashSet<>();
        if (primary != null) authorIds.add(primary.getAuthorId());
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
                HomeResp.SectionData.builder().category(DurationCategory.DEEP).list(toCards(deepList,  userMap)).build()
        );

        return HomeResp.builder().hero(hero).sections(sections).build();
    }


    /**
     * 获取当日 Hero 文章列表。
     * 优先从 Redis 读取当天缓存；缓存失效或不可用时降级为数据库随机查询。
     */
    private List<Article> getOrCacheHeroArticles() {
        String cacheKey = HERO_CACHE_PREFIX + LocalDate.now();

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<Long> ids = objectMapper.readValue(cached, new TypeReference<List<Long>>() {});
                if (!ids.isEmpty()) {
                    List<Article> articles = fetchArticlesByIds(ids);
                    if (!articles.isEmpty()) {
                        return articles;
                    }
                    log.warn("Hero 缓存中的文章均已不存在，清除缓存重新随机选取");
                    redisTemplate.delete(cacheKey);
                }
            }
        } catch (Exception e) {
            log.warn("读取 Hero 缓存失败，降级随机查询: {}", e.getMessage());
            return articleMapper.selectRandomApproved(HERO_TOTAL);
        }

        List<Article> articles = articleMapper.selectRandomApproved(HERO_TOTAL);
        if (articles.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Long> ids = articles.stream().map(Article::getId).collect(Collectors.toList());
            String json = objectMapper.writeValueAsString(ids);
            Duration ttl = Duration.between(LocalDateTime.now(), LocalDate.now().atTime(LocalTime.MAX));
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
            log.info("Hero 每日随机文章已缓存: key={}, ids={}", cacheKey, ids);
        } catch (Exception e) {
            log.warn("写入 Hero 缓存失败: {}", e.getMessage());
        }

        return articles;
    }

    /**
     * 按缓存中的文章 ID 列表回查文章，并保持原始顺序。
     * 已删除或不可见的文章会被过滤掉，避免缓存中出现脏数据时污染首页展示。
     */
    private List<Article> fetchArticlesByIds(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        List<Article> articles = articleMapper.selectBatchIds(ids);

        Map<Long, Article> articleMap = articles.stream()
                .filter(a -> a.getDeleted() == 0)
                .collect(Collectors.toMap(Article::getId, a -> a));

        return ids.stream()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * 批量查询作者摘要信息，避免首页组装过程中出现 N+1 远程调用。
     */
    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> authorIds) {
        if (authorIds.isEmpty()) return Collections.emptyMap();
        BatchUserQueryReq req = new BatchUserQueryReq();
        req.setUserIds(authorIds.stream().toList());
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(req));
        return users.stream().collect(Collectors.toMap(UserSummaryDto::getId, u -> u));
    }

    /**
     * 文章实体到首页卡片响应的字段映射。
     */
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

    /**
     * 批量构造首页卡片列表。
     */
    private List<ArticleCardResp> toCards(List<Article> articles, Map<Long, UserSummaryDto> userMap) {
        return articles.stream().map(a -> toCard(a, userMap)).collect(Collectors.toList());
    }
}
