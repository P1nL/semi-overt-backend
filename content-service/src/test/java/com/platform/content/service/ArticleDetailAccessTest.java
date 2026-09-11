package com.platform.content.service;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.contract.review.dto.ReviewAssignmentDto;
import com.platform.content.api.resp.ArticleDetailResp;
import com.platform.content.entity.Article;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.exception.BusinessException;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleDetailAccessTest {
    @Mock ArticleMapper articleMapper;
    @Mock AuthUserQueryClient authInternalClient;
    @Mock ReviewReasonClient reviewInternalClient;
    @Mock ReviewTaskClient reviewTaskInternalClient;
    @Mock EventOutboxService eventOutboxService;
    @Mock ReviewDecisionService reviewDecisionService;

    private ArticleServiceImpl service() {
        return new ArticleServiceImpl(articleMapper, authInternalClient, reviewInternalClient,
                reviewTaskInternalClient, eventOutboxService, reviewDecisionService);
    }

    @Test
    void authorReadsDatabaseDraftWithoutRedisOverlay() {
        Article article = article(15L, 7L, ArticleStatus.DRAFT);
        article.setContent("database-content");
        when(articleMapper.selectById(15L)).thenReturn(article);
        author(7L);

        ArticleDetailResp response = service().getArticleDetail(15L, 7L);

        assertThat(response.getContent()).isEqualTo("database-content");
        assertThat(response.getVersion()).isEqualTo(3L);
        verify(reviewTaskInternalClient, never()).assignment(any(), any());
    }

    @Test
    void pendingAuthorCanReadWhenProjectionIsNotYetAvailable() {
        Article article = article(16L, 7L, ArticleStatus.PENDING);
        article.setSubmissionId("submission-1");
        when(articleMapper.selectById(16L)).thenReturn(article);
        when(reviewTaskInternalClient.assignment(16L, "submission-1"))
                .thenThrow(new RuntimeException("projection not created yet"));
        author(7L);

        ArticleDetailResp response = service().getArticleDetail(16L, 7L);

        assertThat(response.getAssignedAdminId()).isNull();
        verify(reviewTaskInternalClient).assignment(16L, "submission-1");
    }

    @Test
    void nonAdminNonAuthorIsRejectedWithoutAssignmentRpc() {
        Article article = article(17L, 7L, ArticleStatus.PENDING);
        article.setSubmissionId("submission-2");
        when(articleMapper.selectById(17L)).thenReturn(article);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThatThrownBy(() -> service().getArticleDetail(17L, 8L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(404);
        }
        verify(reviewTaskInternalClient, never()).assignment(any(), any());
    }

    @Test
    void onlyAssignedAdminCanReadPendingDetail() {
        Article article = article(18L, 7L, ArticleStatus.PENDING);
        article.setSubmissionId("submission-3");
        when(articleMapper.selectById(18L)).thenReturn(article);
        when(reviewTaskInternalClient.assignment(18L, "submission-3"))
                .thenReturn(Result.ok(ReviewAssignmentDto.builder()
                        .articleId(18L).submissionId("submission-3").assignedAdminId(9L).build()));
        author(7L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            ArticleDetailResp response = service().getArticleDetail(18L, 9L);
            assertThat(response.getAssignedAdminId()).isEqualTo(9L);
        }
    }

    private void author(Long id) {
        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder().id(id).username("writer").build())));
    }

    private Article article(Long id, Long authorId, ArticleStatus status) {
        Article article = new Article();
        article.setId(id); article.setAuthorId(authorId); article.setStatus(status);
        article.setVersion(3L); article.setDeleted(0); article.setDraftVisible(false);
        return article;
    }
}
