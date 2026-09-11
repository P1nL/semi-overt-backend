package com.platform.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.contract.content.dto.UserProfileArticleStatsDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.contract.review.dto.LatestReviewReasonDto;
import com.platform.content.entity.Article;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileArticlesTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initLambdaCache(Article.class);
    }

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private AuthUserQueryClient authInternalClient;

    @Mock
    private ReviewReasonClient reviewInternalClient;

    @Mock
    private ReviewTaskClient reviewTaskInternalClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private EventOutboxService eventOutboxService;

    @Mock
    private HomeService homeService;

    @Mock
    private ReviewDecisionService reviewDecisionService;

    @Test
    void anonymousViewerOnlyGetsApprovedArticles() {
        ArticleServiceImpl service = service();
        Article approved = buildArticle(11L, 8L, ArticleStatus.APPROVED);
        approved.setContent("approved content");
        approved.setWordCount(300);

        Page<Article> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(approved));

        stubAuthor(8L);
        when(articleMapper.selectProfileStats(8L, false)).thenReturn(stats(1, 0, 0, 0, 0, 300));
        when(articleMapper.selectPage(any(), any())).thenReturn(pageResult);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(null);
            securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

            UserProfileArticlesResp resp = service.getUserProfileArticles(
                    UserProfileArticlesQueryReq.builder()
                            .authorId(8L)
                            .tab("draft")
                            .page(1)
                            .pageSize(10)
                            .build()
            );

            assertThat(resp.getStats().getApproved()).isEqualTo(1);
            assertThat(resp.getStats().getDraft()).isZero();
            assertThat(resp.getStats().getTotalWordCount()).isEqualTo(300);
            assertThat(resp.getList()).hasSize(1);
            assertThat(resp.getList().get(0).getStatus()).isEqualTo(ArticleStatus.APPROVED);
            assertThat(resp.getList().get(0).getAuthorName()).isEqualTo("Writer");
            verify(reviewInternalClient, never()).latestReason(any());
        }
    }

    @Test
    void visitorStatsUseApprovedOnlyAggregateAndNeverLoadAllAuthorRows() {
        ArticleServiceImpl service = service();
        Article approved = buildArticle(100L, 8L, ArticleStatus.APPROVED);
        Page<Article> pageResult = new Page<>(1, 20, 22);
        pageResult.setRecords(List.of(approved));

        stubAuthor(8L);
        when(articleMapper.selectProfileStats(8L, false)).thenReturn(stats(22, 0, 0, 0, 0, 2431));
        when(articleMapper.selectPage(any(), any())).thenReturn(pageResult);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(999L);
            securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

            UserProfileArticlesResp response = service.getUserProfileArticles(
                    UserProfileArticlesQueryReq.builder().authorId(8L).tab("all").build());

            assertThat(response.getStats().getApproved()).isEqualTo(22);
            assertThat(response.getStats().getPending()).isZero();
            assertThat(response.getStats().getReturned()).isZero();
            assertThat(response.getStats().getRejected()).isZero();
            assertThat(response.getStats().getDraft()).isZero();
            assertThat(response.getStats().getTotalWordCount()).isEqualTo(2431);
            verify(articleMapper).selectProfileStats(8L, false);
            verify(articleMapper, never()).selectList(any());
        }
    }

    @Test
    void ownerCanViewRejectedArticlesWithLatestReason() {
        ArticleServiceImpl service = service();
        Article rejected = buildArticle(12L, 8L, ArticleStatus.REJECTED);
        rejected.setContent("rejected content");
        rejected.setWordCount(120);
        Article approved1 = buildArticle(21L, 8L, ArticleStatus.APPROVED);
        Article approved2 = buildArticle(22L, 8L, ArticleStatus.APPROVED);
        Article draft1 = buildArticle(23L, 8L, ArticleStatus.DRAFT);
        Article draft2 = buildArticle(24L, 8L, ArticleStatus.DRAFT);
        Article draft3 = buildArticle(25L, 8L, ArticleStatus.DRAFT);

        Page<Article> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(rejected));

        stubAuthor(8L);
        when(articleMapper.selectProfileStats(8L, true)).thenReturn(stats(2, 0, 0, 1, 3, 120));
        when(articleMapper.selectPage(any(), any())).thenReturn(pageResult);
        when(reviewInternalClient.batchLatestReasons(any())).thenReturn(Result.ok(List.of(LatestReviewReasonDto.builder()
                .articleId(12L)
                .reason("needs more detail")
                .build())));

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(8L);
            securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

            UserProfileArticlesResp resp = service.getUserProfileArticles(
                    UserProfileArticlesQueryReq.builder()
                            .authorId(8L)
                            .tab("rejected")
                            .page(1)
                            .pageSize(10)
                            .build()
            );

            assertThat(resp.getStats().getApproved()).isEqualTo(2);
            assertThat(resp.getStats().getRejected()).isEqualTo(1);
            assertThat(resp.getStats().getDraft()).isEqualTo(3);
            assertThat(resp.getList()).hasSize(1);
            assertThat(resp.getList().get(0).getStatus()).isEqualTo(ArticleStatus.REJECTED);
            assertThat(resp.getList().get(0).getRejectReason()).isEqualTo("needs more detail");
        }
    }

    @Test
    void ownerGetsReturnedReasonInAllTab() {
        ArticleServiceImpl service = service();
        Article returned = buildArticle(13L, 8L, ArticleStatus.RETURNED);
        returned.setContent("returned content");

        Page<Article> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(returned));

        stubAuthor(8L);
        when(articleMapper.selectProfileStats(8L, true)).thenReturn(stats(0, 0, 1, 0, 0, 0));
        when(articleMapper.selectPage(any(), any())).thenReturn(pageResult);
        when(reviewInternalClient.batchLatestReasons(any())).thenReturn(Result.ok(List.of(LatestReviewReasonDto.builder()
                .articleId(13L)
                .reason("revise intro")
                .build())));

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(8L);
            securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

            UserProfileArticlesResp resp = service.getUserProfileArticles(
                    UserProfileArticlesQueryReq.builder()
                            .authorId(8L)
                            .tab("all")
                            .page(1)
                            .pageSize(10)
                            .build()
            );

            assertThat(resp.getList()).hasSize(1);
            assertThat(resp.getList().get(0).getStatus()).isEqualTo(ArticleStatus.RETURNED);
            assertThat(resp.getList().get(0).getRejectReason()).isEqualTo("revise intro");
        }
    }

    @Test
    void adminAggregatesPrivateStatsAndUsesTheSameThreeYearMidnightBoundary() {
        ArticleServiceImpl service = service();
        Article approved = buildArticle(30L, 8L, ArticleStatus.APPROVED);
        Article draft = buildArticle(31L, 8L, ArticleStatus.DRAFT);
        Page<Article> pageResult = new Page<>(1, 100, 2);
        pageResult.setRecords(List.of(approved, draft));

        stubAuthor(8L);
        when(articleMapper.selectProfileStats(8L, true)).thenReturn(stats(22, 1, 0, 0, 1, 2929));
        when(articleMapper.selectWritingCalendar(eq(8L), any(LocalDateTime.class))).thenReturn(List.of());
        when(articleMapper.selectPage(any(), any())).thenReturn(pageResult);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(999L);
            securityUtils.when(SecurityUtils::isAdmin).thenReturn(true);

            UserProfileArticlesResp response = service.getUserProfileArticles(
                    UserProfileArticlesQueryReq.builder()
                            .authorId(8L)
                            .limit(20)
                            .page(1)
                            .pageSize(100)
                            .tab("all")
                            .build());

            assertThat(response.getPageSize()).isEqualTo(100);
            assertThat(response.getStats().getTotalWordCount()).isEqualTo(2929);
            assertThat(response.getStats().getPending()).isEqualTo(1);
            assertThat(response.getStats().getDraft()).isEqualTo(1);
            ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(articleMapper).selectWritingCalendar(eq(8L), since.capture());
            assertThat(since.getValue()).isEqualTo(LocalDate.now().minusYears(3).atStartOfDay());
            verify(articleMapper, never()).selectApprovedWritingCalendar(any(), any());
        }
    }

    private ArticleServiceImpl service() {
        return new ArticleServiceImpl(articleMapper, authInternalClient, reviewInternalClient,
                reviewTaskInternalClient, eventOutboxService, reviewDecisionService);
    }

    private void stubAuthor(Long authorId) {
        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder().id(authorId).username("writer").nickname("Writer").avatarUrl("/a.png").build()
        )));
    }

    private UserProfileArticleStatsDto stats(long approved,
                                             long pending,
                                             long returned,
                                             long rejected,
                                             long draft,
                                             int totalWordCount) {
        return UserProfileArticleStatsDto.builder()
                .approved(approved)
                .pending(pending)
                .returned(returned)
                .rejected(rejected)
                .draft(draft)
                .totalWordCount(totalWordCount)
                .build();
    }

    private Article buildArticle(Long articleId, Long authorId, ArticleStatus status) {
        Article article = new Article();
        article.setId(articleId);
        article.setAuthorId(authorId);
        article.setStatus(status);
        article.setTitle("title-" + articleId);
        article.setSummary("summary-" + articleId);
        article.setWordCount(100);
        return article;
    }
}
