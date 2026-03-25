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
 * 首页服务实现
 *
 * Hero 每日随机逻辑：
 *   - Redis Key：home:hero:{yyyy-MM-dd}，TTL 到当天 23:59:59
 *   - 存储内容：当天随机选出的 5 篇文章 ID（JSON 数组）
 *   - 首次访问：随机查 5 篇 APPROVED 文章，写入 Redis
 *   - 当日后续访问：直接用缓存 ID 查文章，保证同一天展示一致
 *   - 兜底：若 Redis 故障或文章已被删除，降级为按最新时间取
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

    /** Hero 区域随机文章总数（主卡 1 + 次卡 4） */
    private static final int HERO_TOTAL = 5;

    /** 首页各分类 section 取文章条数 */
    private static final int SECTION_LIMIT = 6;

    /** Redis Key 前缀 */
    private static final String HERO_CACHE_PREFIX = "home:hero:";

    @Override
    public HomeResp getHomeData() {
        // ---- 1. 获取 Hero 文章（每日随机，Redis 缓存） ----
        List<Article> heroArticles = getOrCacheHeroArticles();

        // 主卡取第 0 篇，次卡取后 4 篇
        Article primary = heroArticles.isEmpty() ? null : heroArticles.get(0);
        List<Article> secondary = heroArticles.size() > 1
                ? heroArticles.subList(1, heroArticles.size())
                : Collections.emptyList();

        // ---- 2. 查询各分类 section 文章 ----
        List<Article> quickList = articleMapper.selectApprovedByCategory(DurationCategory.QUICK,  SECTION_LIMIT);
        List<Article> shortList = articleMapper.selectApprovedByCategory(DurationCategory.SHORT,  SECTION_LIMIT);
        List<Article> deepList  = articleMapper.selectApprovedByCategory(DurationCategory.DEEP,   SECTION_LIMIT);

        // ---- 3. 汇总所有 authorId，批量查询用户 ----
        Set<Long> authorIds = new HashSet<>();
        if (primary != null) authorIds.add(primary.getAuthorId());
        secondary.forEach(a -> authorIds.add(a.getAuthorId()));
        quickList.forEach(a -> authorIds.add(a.getAuthorId()));
        shortList.forEach(a -> authorIds.add(a.getAuthorId()));
        deepList.forEach(a -> authorIds.add(a.getAuthorId()));

        Map<Long, UserSummaryDto> userMap = batchFetchUsers(authorIds);

        // ---- 4. 组装响应 ----
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

    // ==================== 每日随机 Hero 逻辑 ====================

    /**
     * 获取当天 Hero 文章列表
     * 优先从 Redis 读取当天缓存，缓存不存在则随机选取并写入缓存
     */
    private List<Article> getOrCacheHeroArticles() {
        String cacheKey = HERO_CACHE_PREFIX + LocalDate.now();

        // ---- 尝试从缓存读取文章 ID 列表 ----
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<Long> ids = objectMapper.readValue(cached, new TypeReference<List<Long>>() {});
                if (!ids.isEmpty()) {
                    List<Article> articles = fetchArticlesByIds(ids);
                    if (!articles.isEmpty()) {
                        return articles;
                    }
                    // 缓存命中但文章全部不存在（可能被删除），清除缓存重新随机
                    log.warn("Hero 缓存中的文章均已不存在，清除缓存重新随机选取");
                    redisTemplate.delete(cacheKey);
                }
            }
        } catch (Exception e) {
            // Redis 或反序列化异常：降级为直接随机查询，不影响首页加载
            log.warn("读取 Hero 缓存失败，降级随机查询: {}", e.getMessage());
            return articleMapper.selectRandomApproved(HERO_TOTAL);
        }

        // ---- 缓存不存在：随机取文章并写入缓存 ----
        List<Article> articles = articleMapper.selectRandomApproved(HERO_TOTAL);
        if (articles.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Long> ids = articles.stream().map(Article::getId).collect(Collectors.toList());
            String json = objectMapper.writeValueAsString(ids);
            // TTL = 到今天 23:59:59 的剩余时间，保证次日零点自动刷新
            Duration ttl = Duration.between(LocalDateTime.now(), LocalDate.now().atTime(LocalTime.MAX));
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
            log.info("Hero 每日随机文章已缓存: key={}, ids={}", cacheKey, ids);
        } catch (Exception e) {
            // 写缓存失败：本次请求正常返回，下次请求会再次随机（无持久影响）
            log.warn("写入 Hero 缓存失败: {}", e.getMessage());
        }

        return articles;
    }

    /**
     * 按 ID 列表查询文章，并保持与 ID 列表相同的顺序
     * 过滤掉已被删除或状态变更的文章
     */
    private List<Article> fetchArticlesByIds(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        List<Article> articles = articleMapper.selectBatchIds(ids);

        Map<Long, Article> articleMap = articles.stream()
                .filter(a -> a.getDeleted() == 0)
                .collect(Collectors.toMap(Article::getId, a -> a));

        // 按原始 ID 顺序重组，保证主卡/次卡位置稳定
        return ids.stream()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== 私有辅助方法 ====================

    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> authorIds) {
        if (authorIds.isEmpty()) return Collections.emptyMap();
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
                .rejectReason(null) // 首页不填充 rejectReason
                .build();
    }

    private List<ArticleCardResp> toCards(List<Article> articles, Map<Long, UserSummaryDto> userMap) {
        return articles.stream().map(a -> toCard(a, userMap)).collect(Collectors.toList());
    }
}
