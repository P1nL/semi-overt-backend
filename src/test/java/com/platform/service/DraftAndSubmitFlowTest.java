package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.platform.dto.req.SaveDraftReq;
import com.platform.dto.resp.SubmitResp;
import com.platform.entity.Article;
import com.platform.enums.ArticleStatus;
import com.platform.mapper.ArticleMapper;
import com.platform.mapper.ReviewLogMapper;
import com.platform.mapper.UserMapper;
import com.platform.service.impl.ArticleServiceImpl;
import com.platform.service.impl.DraftServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftAndSubmitFlowTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private ReviewLogMapper reviewLogMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void clearingTitleBeforeSubmitKeepsTitleEmpty() {
        DraftServiceImpl draftService = new DraftServiceImpl(articleMapper, reviewLogMapper, redisTemplate);
        ArticleServiceImpl articleService = new ArticleServiceImpl(
                articleMapper,
                reviewLogMapper,
                userMapper,
                redisTemplate
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

        assertThat(article.getTitle()).isNull();
        assertThat(result.getStatus()).isEqualTo(ArticleStatus.PENDING);
    }
}
