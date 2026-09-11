package com.platform.review.service;

import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.platform.review.entity.ReviewLog;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.service.impl.ReviewDecisionCoordinatorImpl;
import com.platform.review.service.impl.ReviewEventServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewEventServiceImplTest {

    @Mock ReviewTaskService reviewTaskService;
    @Mock ReviewDecisionCoordinator decisionCoordinator;
    @Mock ReviewLogMapper reviewLogMapper;

    private ReviewEventServiceImpl service() {
        return new ReviewEventServiceImpl(reviewTaskService, decisionCoordinator, reviewLogMapper);
    }

    @Test
    void submittedEventProjectsItsOwnVersionedSnapshotWithoutRemoteRead() {
        service().projectPendingTask(ArticleSubmittedEvent.builder()
                .eventId("article-submitted-51")
                .articleId(51L).authorId(7L).title("pending-title").wordCount(345)
                .submitCount(2).submittedAt(LocalDateTime.parse("2026-09-10T18:40:00"))
                .submissionId("submission-51-2").articleVersion(8L).build());

        var captor = ArgumentCaptor.forClass(
                com.platform.contract.review.dto.ReviewTaskUpsertReq.class);
        verify(reviewTaskService).upsertTask(captor.capture());
        assertThat(captor.getValue().getSubmissionId()).isEqualTo("submission-51-2");
        assertThat(captor.getValue().getArticleVersion()).isEqualTo(8L);
        assertThat(captor.getValue().getStatus()).isEqualTo(ArticleStatus.PENDING);
    }

    @Test
    void finalStatusEventProjectsThenFinalizesTheExactDecision() {
        LocalDateTime updatedAt = LocalDateTime.parse("2026-09-10T19:00:00");
        ArticleStatusChangedEvent event = ArticleStatusChangedEvent.builder()
                .eventId("status-52-9").articleId(52L).authorId(8L)
                .fromStatus(ArticleStatus.PENDING).toStatus(ArticleStatus.APPROVED)
                .submissionId("submission-52").articleVersion(9L).updatedAt(updatedAt)
                .decisionId("decision-52").adminId(101L).action(ReviewAction.APPROVE).build();

        service().handleArticleStatusChanged(event);

        verify(reviewTaskService).projectStatus(event);
        var captor = ArgumentCaptor.forClass(ReviewDecisionResultDto.class);
        verify(decisionCoordinator).finalizeAuthoritativeResult(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(ReviewDecisionCoordinatorImpl.FINAL);
        assertThat(captor.getValue().getVersion()).isEqualTo(9L);
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(updatedAt);
        verify(reviewLogMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelWithoutDecisionIdUsesStableGenerationLogKey() {
        when(reviewLogMapper.selectByDecisionId(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        ArticleStatusChangedEvent event = ArticleStatusChangedEvent.builder()
                .eventId("cancel-event").articleId(53L).authorId(9L)
                .fromStatus(ArticleStatus.PENDING).toStatus(ArticleStatus.DRAFT)
                .submissionId("submission-53").articleVersion(4L)
                .action(ReviewAction.CANCEL).build();

        service().handleArticleStatusChanged(event);

        verify(reviewTaskService).projectStatus(event);
        ArgumentCaptor<ReviewLog> captor = ArgumentCaptor.forClass(ReviewLog.class);
        verify(reviewLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(ReviewAction.CANCEL);
        assertThat(captor.getValue().getDecisionId()).hasSize(36);
        assertThat(captor.getValue().getSubmissionId()).isEqualTo("submission-53");
        verify(decisionCoordinator, never()).finalizeAuthoritativeResult(
                org.mockito.ArgumentMatchers.any());
    }
}
