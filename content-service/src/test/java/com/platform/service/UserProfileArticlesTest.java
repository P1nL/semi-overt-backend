package com.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.client.AuthInternalClient;
import com.platform.client.ReviewInternalClient;
import com.platform.common.dto.internal.LatestReviewReasonDto;
import com.platform.common.dto.internal.UserProfileArticlesQueryReq;
import com.platform.common.dto.internal.UserProfileArticlesResp;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.entity.Article;
import com.platform.entity.ReviewLog;
import com.platform.enums.ArticleStatus;
import com.platform.mapper.ArticleMapper;
import com.platform.mapper.ReviewLogMapper;
import com.platform.service.impl.ArticleServiceImpl;
import com.platform.util.Result;
import com.platform.util.SecurityUtils;
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
        MybatisPlusTestSupport.initLambdaCache(Article.class, ReviewLog.class);
    }

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private ReviewLogMapper reviewLogMapper;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private ReviewInternalClient reviewInternalClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void anonymousViewerOnlyGetsApprovedArticles() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, reviewLogMapper, redisTemplate, authInternalClient, reviewInternalClient);

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
                articleMapper, reviewLogMapper, redisTemplate, authInternalClient, reviewInternalClient);

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
