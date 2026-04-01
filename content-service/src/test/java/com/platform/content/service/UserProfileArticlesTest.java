package com.platform.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.dto.LatestReviewReasonDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.events.support.EventOutboxService;
import com.platform.content.entity.Article;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private StringRedisTemplate redisTemplate;

    @Mock
    private EventOutboxService eventOutboxService;

    @Test
    void anonymousViewerOnlyGetsApprovedArticles() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, redisTemplate, authInternalClient, reviewInternalClient, eventOutboxService);

        Article approved = buildArticle(11L, 8L, ArticleStatus.APPROVED);
        approved.setContent("approved content");
        approved.setWordCount(300);

        Page<Article> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(approved));

        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder().id(8L).username("writer").nickname("Writer").avatarUrl("/a.png").build()
        )));
        when(articleMapper.selectCount(any())).thenReturn(1L);
        when(articleMapper.selectList(any())).thenReturn(List.of(approved));
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
            assertThat(resp.getList()).hasSize(1);
            assertThat(resp.getList().get(0).getStatus()).isEqualTo(ArticleStatus.APPROVED);
            assertThat(resp.getList().get(0).getAuthorName()).isEqualTo("Writer");
            verify(reviewInternalClient, never()).latestReason(any());
        }
    }

    @Test
    void ownerCanViewRejectedArticlesWithLatestReason() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, redisTemplate, authInternalClient, reviewInternalClient, eventOutboxService);

        Article rejected = buildArticle(12L, 8L, ArticleStatus.REJECTED);
        rejected.setContent("rejected content");
        rejected.setWordCount(120);

        Page<Article> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(rejected));

        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder().id(8L).username("writer").nickname("Writer").avatarUrl("/a.png").build()
        )));
        when(articleMapper.selectCount(any())).thenReturn(2L, 1L, 0L, 1L, 3L);
        when(articleMapper.selectList(any())).thenReturn(List.of(rejected));
        when(articleMapper.selectPage(any(), any())).thenReturn(pageResult);
        when(reviewInternalClient.latestReason(12L)).thenReturn(Result.ok(LatestReviewReasonDto.builder()
                .articleId(12L)
                .reason("needs more detail")
                .build()));

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
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, redisTemplate, authInternalClient, reviewInternalClient, eventOutboxService);

        Article returned = buildArticle(13L, 8L, ArticleStatus.RETURNED);
        returned.setContent("returned content");

        Page<Article> pageResult = new Page<>(1, 10, 1);
        pageResult.setRecords(List.of(returned));

        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder().id(8L).username("writer").nickname("Writer").avatarUrl("/a.png").build()
        )));
        when(articleMapper.selectCount(any())).thenReturn(2L, 0L, 1L, 0L, 1L);
        when(articleMapper.selectList(any())).thenReturn(List.of(returned));
        when(articleMapper.selectPage(any(), any())).thenReturn(pageResult);
        when(reviewInternalClient.latestReason(13L)).thenReturn(Result.ok(LatestReviewReasonDto.builder()
                .articleId(13L)
                .reason("revise intro")
                .build()));

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

        private Article buildArticle(Long articleId, Long authorId, ArticleStatus status) {
        Article article = new Article();
        article.setId(articleId);
        article.setAuthorId(authorId);
        article.setStatus(status);
        article.setTitle("title-" + articleId);
        article.setSummary("summary-" + articleId);
        return article;
    }
}



