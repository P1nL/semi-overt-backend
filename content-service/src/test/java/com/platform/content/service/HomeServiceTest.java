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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AuthUserQueryClient authInternalClient;

    @Test
    void anonymousHomeHasElevenHeroCardsAndNeverWritesExposure() {
        HomeServiceImpl service = service();
        List<Article> quick = articles(1, 11, 101L, DurationCategory.QUICK);
        when(articleMapper.selectApprovedByCategory(DurationCategory.QUICK, 11)).thenReturn(quick);
        when(articleMapper.selectApprovedByCategory(DurationCategory.SHORT, 11)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategory(DurationCategory.DEEP, 11)).thenReturn(List.of());
        stubAuthors(101L);

        HomeResp response = service.getHomeData();

        assertThat(response.getHero().getPrimary().getId()).isEqualTo(1L);
        assertThat(response.getHero().getSecondary()).hasSize(10);
        assertThat(response.getHero().getSecondary())
                .extracting("id")
                .containsExactly(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        assertThat(response.getSections()).hasSize(3);
        assertThat(response.getSections().get(0).getCode()).isEqualTo("QUICK");
        assertThat(response.getSections().get(0).getArticles()).hasSize(11);
        assertThat(response.getSections().get(1).getArticles()).isEmpty();
        verify(articleMapper).selectApprovedByCategory(DurationCategory.QUICK, 11);
        verify(articleMapper, never()).recordHomeExposure(anyLong(), anyLong());
    }

    @Test
    void authenticatedHomeUsesPerUserSelectionAndExposureRows() {
        HomeServiceImpl service = service();
        List<Article> quick = articles(1, 11, 101L, DurationCategory.QUICK);
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.QUICK, 11)).thenReturn(quick);
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.SHORT, 11)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.DEEP, 11)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategoryForHomeUser(43L, DurationCategory.QUICK, 11)).thenReturn(quick);
        when(articleMapper.selectApprovedByCategoryForHomeUser(43L, DurationCategory.SHORT, 11)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategoryForHomeUser(43L, DurationCategory.DEEP, 11)).thenReturn(List.of());
        stubAuthors(101L);

        service.getHomeData(42L);
        service.getHomeData(43L);

        verify(articleMapper).selectApprovedByCategoryForHomeUser(42L, DurationCategory.QUICK, 11);
        verify(articleMapper).selectApprovedByCategoryForHomeUser(43L, DurationCategory.QUICK, 11);
        verify(articleMapper, never()).selectApprovedByCategory(any(), eq(11));
        verify(articleMapper, org.mockito.Mockito.times(11)).recordHomeExposure(eq(42L), anyLong());
        verify(articleMapper, org.mockito.Mockito.times(11)).recordHomeExposure(eq(43L), anyLong());
    }

    @Test
    void repeatedHeroIdsAreDeduplicatedBeforeExposureWrite() {
        HomeServiceImpl service = service();
        List<Article> quick = new ArrayList<>();
        quick.add(article(1L, 101L, DurationCategory.QUICK));
        quick.add(article(1L, 101L, DurationCategory.QUICK));
        quick.add(article(2L, 101L, DurationCategory.QUICK));
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.QUICK, 11)).thenReturn(quick);
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.SHORT, 11)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.DEEP, 11)).thenReturn(List.of());
        stubAuthors(101L);

        service.getHomeData(42L);

        verify(articleMapper).recordHomeExposure(42L, 1L);
        verify(articleMapper).recordHomeExposure(42L, 2L);
        verify(articleMapper, org.mockito.Mockito.times(2)).recordHomeExposure(anyLong(), anyLong());
    }

    @Test
    void authorLookupFailurePreventsExposureWrite() {
        HomeServiceImpl service = service();
        List<Article> quick = articles(1, 11, 101L, DurationCategory.QUICK);
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.QUICK, 11)).thenReturn(quick);
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.SHORT, 11)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.DEEP, 11)).thenReturn(List.of());
        when(authInternalClient.batchUsers(any())).thenThrow(new IllegalStateException("auth unavailable"));

        assertThatThrownBy(() -> service.getHomeData(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("auth unavailable");
        verify(articleMapper, never()).recordHomeExposure(anyLong(), anyLong());
    }

    @Test
    void exposureWriteFailurePropagatesInsteadOfReturningSuccess() {
        HomeServiceImpl service = service();
        List<Article> quick = articles(1, 11, 101L, DurationCategory.QUICK);
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.QUICK, 11)).thenReturn(quick);
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.SHORT, 11)).thenReturn(List.of());
        when(articleMapper.selectApprovedByCategoryForHomeUser(42L, DurationCategory.DEEP, 11)).thenReturn(List.of());
        stubAuthors(101L);
        when(articleMapper.recordHomeExposure(42L, 1L)).thenThrow(new IllegalStateException("exposure db unavailable"));

        assertThatThrownBy(() -> service.getHomeData(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("exposure db unavailable");
    }

    private HomeServiceImpl service() {
        return new HomeServiceImpl(articleMapper, redisTemplate, new ObjectMapper(), authInternalClient);
    }

    private void stubAuthors(Long authorId) {
        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder()
                        .id(authorId)
                        .username("writer-" + authorId)
                        .nickname("Writer " + authorId)
                        .avatarUrl("/avatar-" + authorId + ".png")
                        .build())));
    }

    private List<Article> articles(int from, int to, Long authorId, DurationCategory category) {
        List<Article> result = new ArrayList<>();
        for (int id = from; id <= to; id++) {
            result.add(article((long) id, authorId, category));
        }
        return result;
    }

    private Article article(Long articleId, Long authorId, DurationCategory category) {
        Article article = new Article();
        article.setId(articleId);
        article.setAuthorId(authorId);
        article.setTitle("title-" + articleId);
        article.setSummary("summary-" + articleId);
        article.setContent("content-" + articleId);
        article.setStatus(ArticleStatus.APPROVED);
        article.setDurationCategory(category);
        article.setReadMinutes(BigDecimal.valueOf(5));
        article.setWordCount(100);
        article.setPublishedAt(LocalDateTime.now().minusDays(articleId));
        article.setUpdatedAt(article.getPublishedAt());
        return article;
    }
}
