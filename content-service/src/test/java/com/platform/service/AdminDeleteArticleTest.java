package com.platform.service;

import com.platform.client.AuthInternalClient;
import com.platform.client.ReviewInternalClient;
import com.platform.common.support.EventOutboxService;
import com.platform.entity.Article;
import com.platform.enums.ArticleStatus;
import com.platform.exception.BusinessException;
import com.platform.mapper.ArticleMapper;
import com.platform.service.impl.ArticleServiceImpl;
import com.platform.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminDelete文章Test业务接口，定义对外暴露的服务能力。
 */

@ExtendWith(MockitoExtension.class)
class AdminDeleteArticleTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private ReviewInternalClient reviewInternalClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private EventOutboxService eventOutboxService;

    @Test
    void adminCanDeletePendingArticle() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, redisTemplate, authInternalClient, reviewInternalClient, eventOutboxService);
        Article article = buildArticle(12L, 3L, ArticleStatus.PENDING);
        when(articleMapper.selectById(12L)).thenReturn(article);

        try (MockedStatic<SecurityUtils> securityUtils = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::isAdmin).thenReturn(true);

            Map<String, Object> result = service.adminDeleteArticle(12L, 99L);

            assertThat(result).containsEntry("ok", true);
            verify(articleMapper).deleteById(12L);
            verify(redisTemplate).delete("draft:3:12");
        }
    }

    @Test
    void adminCanDeleteApprovedArticle() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, redisTemplate, authInternalClient, reviewInternalClient, eventOutboxService);
        Article article = buildArticle(13L, 4L, ArticleStatus.APPROVED);
        when(articleMapper.selectById(13L)).thenReturn(article);

        try (MockedStatic<SecurityUtils> securityUtils = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::isAdmin).thenReturn(true);

            Map<String, Object> result = service.adminDeleteArticle(13L, 100L);

            assertThat(result).containsEntry("ok", true);
            verify(articleMapper).deleteById(13L);
            verify(redisTemplate).delete("draft:4:13");
        }
    }

    @Test
    void nonAdminCannotDeleteThroughAdminCapability() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper, redisTemplate, authInternalClient, reviewInternalClient, eventOutboxService);

        try (MockedStatic<SecurityUtils> securityUtils = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::isAdmin).thenReturn(false);

            assertThatThrownBy(() -> service.adminDeleteArticle(20L, 2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(403);
        }
    }

    /**
     * 定义构建文章的服务能力。
     */
    private Article buildArticle(Long articleId, Long authorId, ArticleStatus status) {
        Article article = new Article();
        article.setId(articleId);
        article.setAuthorId(authorId);
        article.setStatus(status);
        return article;
    }
}
