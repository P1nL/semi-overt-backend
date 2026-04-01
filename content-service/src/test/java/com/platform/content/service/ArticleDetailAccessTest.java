package com.platform.content.service;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.events.support.EventOutboxService;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.content.api.resp.ArticleDetailResp;
import com.platform.content.entity.Article;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.exception.BusinessException;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.kernel.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ArticleDetailAccessTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthUserQueryClient authInternalClient;

    @Mock
    private ReviewReasonClient reviewInternalClient;

    @Mock
    private EventOutboxService eventOutboxService;

    @Test
    void authorCanReadOwnDraftWithout404AndGetsRedisContent() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, redisTemplate, authInternalClient, reviewInternalClient, eventOutboxService);

        Article article = new Article();
        article.setId(15L);
        article.setAuthorId(7L);
        article.setStatus(ArticleStatus.DRAFT);
        article.setContent("mysql-content");

        when(articleMapper.selectById(15L)).thenReturn(article);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("draft:7:15")).thenReturn("redis-content");
        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(java.util.List.of(
                UserSummaryDto.builder().id(7L).username("alice").avatarUrl("/avatar.png").build()
        )));

        ArticleDetailResp resp = service.getArticleDetail(15L, 7L);

        assertThat(resp.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        assertThat(resp.getContent()).isEqualTo("redis-content");
        assertThat(resp.getAuthor().getUsername()).isEqualTo("alice");
        verify(reviewInternalClient, never()).latestReason(15L);
    }

    @Test
    void anonymousCanReadApprovedButNotReturnedArticle() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, redisTemplate, authInternalClient, reviewInternalClient, eventOutboxService);

        Article approved = new Article();
        approved.setId(16L);
        approved.setAuthorId(8L);
        approved.setStatus(ArticleStatus.APPROVED);
        approved.setContent("public-content");

        Article returned = new Article();
        returned.setId(17L);
        returned.setAuthorId(8L);
        returned.setStatus(ArticleStatus.RETURNED);
        returned.setContent("private-content");

        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(java.util.List.of(
                UserSummaryDto.builder().id(8L).username("writer").avatarUrl("/avatar.png").build()
        )));
        when(articleMapper.selectById(16L)).thenReturn(approved);
        when(articleMapper.selectById(17L)).thenReturn(returned);

        try (MockedStatic<com.platform.kernel.util.SecurityUtils> securityUtils = mockStatic(com.platform.kernel.util.SecurityUtils.class)) {
            securityUtils.when(com.platform.kernel.util.SecurityUtils::isAdmin).thenReturn(false);

            ArticleDetailResp resp = service.getArticleDetail(16L, null);
            assertThat(resp.getContent()).isEqualTo("public-content");

            assertThatThrownBy(() -> service.getArticleDetail(17L, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(404);
        }
    }
}



