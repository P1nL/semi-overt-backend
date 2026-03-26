package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.platform.client.AuthInternalClient;
import com.platform.client.ReviewInternalClient;
import com.platform.common.dto.internal.ReviewTaskRemoveReq;
import com.platform.common.dto.internal.ReviewTaskUpsertReq;
import com.platform.dto.req.SaveDraftReq;
import com.platform.dto.resp.SubmitResp;
import com.platform.entity.Article;
import com.platform.enums.ArticleStatus;
import com.platform.mapper.ArticleMapper;
import com.platform.service.impl.ArticleServiceImpl;
import com.platform.service.impl.DraftServiceImpl;
import com.platform.util.Result;
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
    private AuthInternalClient authInternalClient;

    @Mock
    private ReviewInternalClient reviewInternalClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void clearingTitleBeforeSubmitKeepsTitleEmptyAndCreatesProjectionTask() {
        DraftServiceImpl draftService = new DraftServiceImpl(articleMapper, redisTemplate, reviewInternalClient);
        ArticleServiceImpl articleService = new ArticleServiceImpl(
                articleMapper,
                redisTemplate,
                authInternalClient,
                reviewInternalClient
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
        when(reviewInternalClient.upsertTask(any())).thenReturn(Result.ok());

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

        ArgumentCaptor<ReviewTaskUpsertReq> taskCaptor = ArgumentCaptor.forClass(ReviewTaskUpsertReq.class);

        assertThat(article.getTitle()).isNull();
        assertThat(result.getStatus()).isEqualTo(ArticleStatus.PENDING);
        verify(redisTemplate).delete("draft:" + userId + ":" + articleId);
        verify(reviewInternalClient).upsertTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getArticleId()).isEqualTo(articleId);
        assertThat(taskCaptor.getValue().getAuthorId()).isEqualTo(userId);
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo(ArticleStatus.PENDING);
    }

    @Test
    void cancelReviewRemovesProjectionTask() {
        ArticleServiceImpl articleService = new ArticleServiceImpl(
                articleMapper,
                redisTemplate,
                authInternalClient,
                reviewInternalClient
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
        when(reviewInternalClient.removeTask(any())).thenReturn(Result.ok());

        Map<String, Object> result = articleService.cancelReview(articleId, userId);

        ArgumentCaptor<ReviewTaskRemoveReq> removeCaptor = ArgumentCaptor.forClass(ReviewTaskRemoveReq.class);

        assertThat(result).containsEntry("status", ArticleStatus.DRAFT);
        assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);
        verify(reviewInternalClient).removeTask(removeCaptor.capture());
        assertThat(removeCaptor.getValue().getArticleId()).isEqualTo(articleId);
        assertThat(removeCaptor.getValue().getLastEventId()).startsWith("cancel-sync:");
    }
}
