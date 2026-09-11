package com.platform.content.service;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.content.entity.Article;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.enums.ArticleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDeleteArticleTest {
    @Mock ArticleMapper articleMapper;
    @Mock AuthUserQueryClient authInternalClient;
    @Mock ReviewReasonClient reviewInternalClient;
    @Mock ReviewTaskClient reviewTaskInternalClient;
    @Mock EventOutboxService eventOutboxService;
    @Mock ReviewDecisionService reviewDecisionService;

    @Test
    void authorCanDeleteApprovedArticleWithVersionedEvent() {
        Article before = article(13L, 4L, ArticleStatus.APPROVED, 6L, 0);
        Article after = article(13L, 4L, ArticleStatus.APPROVED, 7L, 1);
        when(articleMapper.selectByIdForUpdate(13L)).thenReturn(before);
        when(articleMapper.deleteByAuthorCas(13L, 4L, 6L)).thenReturn(1);
        when(articleMapper.selectByIdIncludingDeleted(13L)).thenReturn(after);

        service().deleteArticle(13L, 4L);

        verify(articleMapper).deleteByAuthorCas(13L, 4L, 6L);
        verify(eventOutboxService).saveEvent(any(), any(), any(), any());
    }

    private ArticleServiceImpl service() {
        return new ArticleServiceImpl(articleMapper, authInternalClient, reviewInternalClient,
                reviewTaskInternalClient, eventOutboxService, reviewDecisionService);
    }

    private Article article(Long id, Long author, ArticleStatus status, Long version, int deleted) {
        Article value = new Article();
        value.setId(id); value.setAuthorId(author); value.setStatus(status);
        value.setVersion(version); value.setDeleted(deleted);
        value.setUpdatedAt(java.time.LocalDateTime.now());
        return value;
    }
}
