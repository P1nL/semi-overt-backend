package com.platform.content.service;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.content.api.req.SaveDraftReq;
import com.platform.content.api.resp.SaveDraftResp;
import com.platform.content.api.resp.SubmitResp;
import com.platform.content.entity.Article;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.content.service.impl.DraftServiceImpl;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.platform.kernel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftAndSubmitFlowTest {
    @Mock ArticleMapper articleMapper;
    @Mock AuthUserQueryClient authInternalClient;
    @Mock ReviewReasonClient reviewInternalClient;
    @Mock ReviewTaskClient reviewTaskInternalClient;
    @Mock EventOutboxService eventOutboxService;
    @Mock ReviewDecisionService reviewDecisionService;

    @Test
    void emptyStringClearsFieldAndSaveReturnsDatabaseVersion() {
        Article before = article(8L, 1L, ArticleStatus.DRAFT, 2L);
        before.setTitle("old"); before.setContent("正文");
        Article after = article(8L, 1L, ArticleStatus.DRAFT, 3L);
        after.setTitle(""); after.setContent("正文"); after.setWordCount(2);
        after.setReadMinutes(BigDecimal.valueOf(0.1)); after.setDurationCategory(DurationCategory.QUICK);
        after.setUpdatedAt(LocalDateTime.now());
        when(articleMapper.selectById(8L)).thenReturn(before, after);
        when(articleMapper.updateDraftFields(eq(8L), eq(1L), eq(2L), eq(""), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        SaveDraftReq request = new SaveDraftReq(); request.setTitle("");

        SaveDraftResp response = new DraftServiceImpl(articleMapper, reviewInternalClient)
                .saveDraft(8L, 1L, request);

        assertThat(response.getVersion()).isEqualTo(3L);
        verify(articleMapper).updateDraftFields(eq(8L), eq(1L), eq(2L), eq(""), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void contentOverFifteenThousandIsRejectedBeforeAnyUpdate() {
        Article before = article(9L, 1L, ArticleStatus.DRAFT, 7L);
        before.setContent("old");
        when(articleMapper.selectById(9L)).thenReturn(before);
        SaveDraftReq request = new SaveDraftReq(); request.setContent("字".repeat(15_001)); request.setVersion(7L);

        assertThatThrownBy(() -> new DraftServiceImpl(articleMapper, reviewInternalClient)
                .saveDraft(9L, 1L, request))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(400);
        verify(articleMapper, never()).updateDraftFields(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(before.getVersion()).isEqualTo(7L);
        assertThat(before.getStatus()).isEqualTo(ArticleStatus.DRAFT);
    }

    @Test
    void submitHasNoLegacyCooldownAndPublishesVersionedGeneration() {
        Article before = article(10L, 2L, ArticleStatus.RETURNED, 4L);
        before.setContent("足够长度正文".repeat(20)); before.setLastSubmittedAt(LocalDateTime.now());
        Article after = article(10L, 2L, ArticleStatus.PENDING, 5L);
        after.setContent(before.getContent()); after.setSubmissionId("submission-1");
        after.setSubmitCount(2); after.setLastSubmittedAt(LocalDateTime.now()); after.setUpdatedAt(LocalDateTime.now());
        when(articleMapper.selectByIdForUpdate(10L)).thenReturn(before);
        when(articleMapper.submitForReview(eq(10L), eq(2L), eq(4L), any())).thenReturn(1);
        when(articleMapper.selectByIdIncludingDeleted(10L)).thenReturn(after);

        SubmitResp response = articleService().submitForReview(10L, 2L);

        assertThat(response.getSubmissionId()).isEqualTo("submission-1");
        ArgumentCaptor<ArticleSubmittedEvent> event = ArgumentCaptor.forClass(ArticleSubmittedEvent.class);
        verify(eventOutboxService).saveEvent(any(), any(), any(), event.capture());
        assertThat(event.getValue().getArticleVersion()).isEqualTo(5L);
        assertThat(event.getValue().getSubmissionId()).isEqualTo("submission-1");
    }

    @Test
    void cancelWritesOutboxWithoutRemoteTaskDeletion() {
        Article before = article(11L, 3L, ArticleStatus.PENDING, 8L);
        before.setSubmissionId("submission-2");
        Article after = article(11L, 3L, ArticleStatus.DRAFT, 9L);
        after.setSubmissionId("submission-2"); after.setUpdatedAt(LocalDateTime.now());
        when(articleMapper.selectByIdForUpdate(11L)).thenReturn(before);
        when(articleMapper.cancelReview(11L, 3L, 8L, "submission-2")).thenReturn(1);
        when(articleMapper.selectByIdIncludingDeleted(11L)).thenReturn(after);

        articleService().cancelReview(11L, 3L);

        verify(reviewTaskInternalClient, never()).removeTask(any());
        ArgumentCaptor<ArticleStatusChangedEvent> event = ArgumentCaptor.forClass(ArticleStatusChangedEvent.class);
        verify(eventOutboxService).saveEvent(any(), any(), any(), event.capture());
        assertThat(event.getValue().getSubmissionId()).isEqualTo("submission-2");
        assertThat(event.getValue().getArticleVersion()).isEqualTo(9L);
        assertThat(event.getValue().getAction()).isEqualTo(com.platform.kernel.enums.ReviewAction.CANCEL);
    }

    private ArticleServiceImpl articleService() {
        return new ArticleServiceImpl(articleMapper, authInternalClient, reviewInternalClient,
                reviewTaskInternalClient, eventOutboxService, reviewDecisionService);
    }

    private Article article(Long id, Long author, ArticleStatus status, Long version) {
        Article value = new Article();
        value.setId(id); value.setAuthorId(author); value.setStatus(status); value.setVersion(version);
        value.setDeleted(0); value.setDraftVisible(false); value.setSubmitCount(1);
        value.setWordCount(0); value.setReadMinutes(BigDecimal.ZERO); value.setDurationCategory(DurationCategory.QUICK);
        return value;
    }
}
