package com.platform.content.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.platform.events.support.EventOutboxService;
import com.platform.content.api.req.SaveDraftReq;
import com.platform.content.api.resp.SubmitResp;
import com.platform.content.entity.Article;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.content.service.impl.DraftServiceImpl;
import com.platform.kernel.util.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class DraftAndSubmitFlowTest {

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
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EventOutboxService eventOutboxService;

    @Test
    void clearingTitleBeforeSubmitKeepsTitleEmptyAndCreatesProjectionTask() {
        DraftServiceImpl draftService = new DraftServiceImpl(articleMapper, redisTemplate, reviewInternalClient);
        ArticleServiceImpl articleService = new ArticleServiceImpl(
                articleMapper,
                redisTemplate,
                authInternalClient,
                reviewInternalClient,
                eventOutboxService
        );

        Long articleId = 8L;
        Long userId = 1L;
        String latestContent = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz1234567890";

        Article article = new Article();
        article.setId(articleId);
        article.setAuthorId(userId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setTitle("old-title");
        article.setContent("old-content");
        article.setSubmitCount(0);

        when(articleMapper.selectById(articleId)).thenReturn(article);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("draft:" + userId + ":" + articleId)).thenReturn(latestContent);
        doAnswer(invocation -> {
            LambdaUpdateWrapper<Article> wrapper = invocation.getArgument(1);
            assertThat(wrapper.getSqlSet()).contains("title");
            article.setTitle(null);
            return 1;
        }).when(articleMapper).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<Article>>any());

        SaveDraftReq req = new SaveDraftReq();
        req.setTitle("");
        req.setSummary("summary");
        req.setContent(latestContent);

        draftService.saveDraft(articleId, userId, req);
        SubmitResp result = articleService.submitForReview(articleId, userId);

        ArgumentCaptor<ArticleSubmittedEvent> eventCaptor = ArgumentCaptor.forClass(ArticleSubmittedEvent.class);

        assertThat(article.getTitle()).isNull();
        assertThat(result.getStatus()).isEqualTo(ArticleStatus.PENDING);
        verify(redisTemplate).delete("draft:" + userId + ":" + articleId);
        verify(eventOutboxService).saveEvent(any(), any(), any(), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getArticleId()).isEqualTo(articleId);
        assertThat(eventCaptor.getValue().getAuthorId()).isEqualTo(userId);
        assertThat(eventCaptor.getValue().getSubmitCount()).isEqualTo(1);
    }

    @Test
    void cancelReviewRemovesProjectionTask() {
        ArticleServiceImpl articleService = new ArticleServiceImpl(
                articleMapper,
                redisTemplate,
                authInternalClient,
                reviewInternalClient,
                eventOutboxService
        );

        Long articleId = 18L;
        Long userId = 3L;

        Article article = new Article();
        article.setId(articleId);
        article.setAuthorId(userId);
        article.setStatus(ArticleStatus.PENDING);
        article.setSubmitCount(2);
        article.setLastSubmittedAt(java.time.LocalDateTime.parse("2026-03-26T10:15:30"));

        when(articleMapper.selectById(articleId)).thenReturn(article);
        Map<String, Object> result = articleService.cancelReview(articleId, userId);

        ArgumentCaptor<ArticleStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(ArticleStatusChangedEvent.class);

        assertThat(result).containsEntry("status", ArticleStatus.DRAFT);
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        verify(eventOutboxService).saveEvent(any(), any(), any(), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getArticleId()).isEqualTo(articleId);
        assertThat(eventCaptor.getValue().getFromStatus()).isEqualTo(ArticleStatus.PENDING);
        assertThat(eventCaptor.getValue().getToStatus()).isEqualTo(ArticleStatus.DRAFT);
    }
}



