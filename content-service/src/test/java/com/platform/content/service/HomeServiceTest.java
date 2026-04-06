package com.platform.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.content.api.resp.HomeResp;
import com.platform.content.entity.Article;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.HomeServiceImpl;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import com.platform.kernel.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthUserQueryClient authInternalClient;

    @Test
    void homeHeroReturnsElevenArticlesWithTenSecondaryCards() {
        HomeServiceImpl service = new HomeServiceImpl(
                articleMapper,
                redisTemplate,
                new ObjectMapper(),
                authInternalClient
        );

        List<Article> heroArticles = List.of(
                buildArticle(1L, 101L),
                buildArticle(2L, 102L),
                buildArticle(3L, 103L),
                buildArticle(4L, 104L),
                buildArticle(5L, 105L),
                buildArticle(6L, 106L),
                buildArticle(7L, 107L),
                buildArticle(8L, 108L),
                buildArticle(9L, 109L),
                buildArticle(10L, 110L),
                buildArticle(11L, 111L)
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);
        when(articleMapper.selectRandomApproved(11)).thenReturn(heroArticles);
        when(articleMapper.selectApprovedByCategory(DurationCategory.QUICK, 6)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategory(DurationCategory.SHORT, 6)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategory(DurationCategory.DEEP, 6)).thenReturn(List.of());
        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(heroArticles.stream()
                .map(article -> UserSummaryDto.builder()
                        .id(article.getAuthorId())
                        .username("user" + article.getAuthorId())
                        .nickname("Author " + article.getAuthorId())
                        .avatarUrl("/avatar-" + article.getAuthorId() + ".png")
                        .build())
                .toList()));

        HomeResp resp = service.getHomeData();

        assertThat(resp.getHero().getPrimary()).isNotNull();
        assertThat(resp.getHero().getPrimary().getArticleId()).isEqualTo(1L);
        assertThat(resp.getHero().getSecondary()).hasSize(10);
        assertThat(resp.getHero().getSecondary())
                .extracting("articleId")
                .containsExactly(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        verify(articleMapper).selectRandomApproved(11);
        verify(valueOperations).get(eq("home:hero:v11:" + LocalDate.now()));
    }

    private Article buildArticle(Long articleId, Long authorId) {
        Article article = new Article();
        article.setId(articleId);
        article.setAuthorId(authorId);
        article.setTitle("title-" + articleId);
        article.setSummary("summary-" + articleId);
        article.setContent("content-" + articleId);
        article.setStatus(ArticleStatus.APPROVED);
        article.setDurationCategory(DurationCategory.SHORT);
        article.setReadMinutes(BigDecimal.valueOf(5));
        return article;
    }
}
