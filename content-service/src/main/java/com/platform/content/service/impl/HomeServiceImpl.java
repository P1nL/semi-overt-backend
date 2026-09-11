package com.platform.content.service.impl;

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
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeServiceImpl implements HomeService {

    private static final int CARD_PREVIEW_MAX_LENGTH = 120;
    private static final int HERO_TOTAL = 11;
    private static final int SECTION_LIMIT = 11;
    private static final String LEGACY_HERO_CACHE_PREFIX = "home:hero:v";

    // Keep the existing constructor shape so S3 tests and the running service do
    // not need an unrelated dependency-boundary change. Package 1 does not use
    // Redis or a global last_featured_at cache for selection.
    private final ArticleMapper articleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthUserQueryClient authInternalClient;

    @Override
    public HomeResp getHomeData(Long currentUserId) {
        List<Article> quick = selectHomeArticles(currentUserId, DurationCategory.QUICK);
        List<Article> shortRead = selectHomeArticles(currentUserId, DurationCategory.SHORT);
        List<Article> deep = selectHomeArticles(currentUserId, DurationCategory.DEEP);

        List<Article> allArticles = new java.util.ArrayList<>(quick.size() + shortRead.size() + deep.size());
        allArticles.addAll(quick);
        allArticles.addAll(shortRead);
        allArticles.addAll(deep);

        List<Article> heroArticles = allArticles.size() <= HERO_TOTAL
                ? allArticles
                : allArticles.subList(0, HERO_TOTAL);

        Article primary = heroArticles.isEmpty() ? null : heroArticles.get(0);
        List<Article> secondary = heroArticles.size() <= 1
                ? Collections.emptyList()
                : heroArticles.subList(1, heroArticles.size());

        Set<Long> authorIds = allArticles.stream()
                .map(Article::getAuthorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserSummaryDto> userMap = batchFetchUsers(authorIds);
        if (currentUserId != null) {
            recordHeroExposures(currentUserId, heroArticles);
        }

        HomeResp.HeroData hero = HomeResp.HeroData.builder()
                .primary(primary == null ? null : toCard(primary, userMap))
                .secondary(toCards(secondary, userMap))
                .build();

        List<HomeResp.SectionData> sections = List.of(
                section(DurationCategory.QUICK, quick, userMap),
                section(DurationCategory.SHORT, shortRead, userMap),
                section(DurationCategory.DEEP, deep, userMap)
        );
        return HomeResp.builder().hero(hero).sections(sections).build();
    }

    private HomeResp.SectionData section(DurationCategory category,
                                         List<Article> articles,
                                         Map<Long, UserSummaryDto> userMap) {
        List<ArticleCardResp> cards = toCards(articles, userMap);
        String name = switch (category) {
            case QUICK -> "Quick Read";
            case SHORT -> "Short Read";
            case DEEP -> "Deep Read";
        };
        return HomeResp.SectionData.builder()
                .category(category)
                .code(category.name())
                .name(name)
                .list(cards)
                .articles(cards)
                .build();
    }

    private List<Article> selectHomeArticles(Long currentUserId, DurationCategory category) {
        if (currentUserId == null) {
            return articleMapper.selectApprovedByCategory(category, SECTION_LIMIT);
        }
        return articleMapper.selectApprovedByCategoryForHomeUser(currentUserId, category, SECTION_LIMIT);
    }

    private void recordHeroExposures(Long userId, List<Article> heroArticles) {
        heroArticles.stream()
                .map(Article::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(articleId -> articleMapper.recordHomeExposure(userId, articleId));
    }

    @Override
    public void invalidateHeroCache() {
        // This hook remains for S3 approval flow. It only removes a legacy key;
        // current selection is DB-authoritative and user-scoped.
        try {
            redisTemplate.delete(LEGACY_HERO_CACHE_PREFIX + HERO_TOTAL + ":" + LocalDate.now());
        } catch (RuntimeException ex) {
            log.debug("Legacy hero cache invalidation skipped: {}", ex.getMessage());
        }
    }

    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        BatchUserQueryReq req = new BatchUserQueryReq();
        req.setUserIds(authorIds.stream().toList());
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(req));
        if (users == null) {
            return Collections.emptyMap();
        }
        return users.stream().collect(Collectors.toMap(UserSummaryDto::getId, u -> u, (first, ignored) -> first));
    }

    private ArticleCardResp toCard(Article article, Map<Long, UserSummaryDto> userMap) {
        UserSummaryDto author = userMap.get(article.getAuthorId());
        ArticleCardResp.AuthorInfo authorInfo = author == null ? null : ArticleCardResp.AuthorInfo.builder()
                .id(author.getId())
                .username(author.getUsername())
                .nickname(author.getNickname())
                .avatarUrl(author.getAvatarUrl())
                .build();
        LocalDateTime publishedAt = article.getStatus() == ArticleStatus.APPROVED
                ? (article.getPublishedAt() == null ? article.getUpdatedAt() : article.getPublishedAt())
                : null;
        return ArticleCardResp.builder()
                .id(article.getId())
                .articleId(article.getId())
                .author(authorInfo)
                .title(article.getTitle())
                .summary(article.getSummary())
                .previewText(ArticleUtils.extractPreviewText(article.getContent(), CARD_PREVIEW_MAX_LENGTH))
                .coverUrl(article.getCoverUrl())
                .coverColor(article.getCoverColor())
                .readMinutes(article.getReadMinutes())
                .durationCategory(article.getDurationCategory())
                .status(article.getStatus())
                .wordCount(article.getWordCount())
                .draftVisible(Boolean.TRUE.equals(article.getDraftVisible()))
                .authorId(author != null ? author.getId() : article.getAuthorId())
                .authorName(author != null ? author.getNickname() : null)
                .authorAvatar(author != null ? author.getAvatarUrl() : null)
                .publishedAt(publishedAt)
                .updatedAt(article.getUpdatedAt())
                .rejectReason(null)
                .build();
    }

    private List<ArticleCardResp> toCards(List<Article> articles, Map<Long, UserSummaryDto> userMap) {
        return articles == null ? List.of() : articles.stream().map(a -> toCard(a, userMap)).toList();
    }
}
