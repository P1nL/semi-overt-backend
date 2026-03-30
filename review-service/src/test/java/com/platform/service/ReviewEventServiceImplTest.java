package com.platform.service;

import com.platform.client.ContentInternalClient;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.common.event.ArticleSubmittedEvent;
import com.platform.entity.ReviewLog;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import com.platform.mapper.ReviewLogMapper;
import com.platform.service.impl.ReviewEventServiceImpl;
import com.platform.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审核事件服务ImplTest业务接口，定义对外暴露的服务能力。
 */

@ExtendWith(MockitoExtension.class)
class ReviewEventServiceImplTest {

    @Mock
    private ContentInternalClient contentInternalClient;

    @Mock
    private ReviewTaskService reviewTaskService;

    @Mock
    private ReviewLogMapper reviewLogMapper;

    @Test
    void articleSubmittedProjectsPendingTaskFromSnapshot() {
        ReviewEventServiceImpl service = new ReviewEventServiceImpl(
                contentInternalClient,
                reviewTaskService,
                reviewLogMapper
        );

        when(contentInternalClient.reviewSnapshot(51L)).thenReturn(Result.ok(ArticleReviewSnapshotDto.builder()
                .articleId(51L)
                .authorId(7L)
                .title("pending-title")
                .wordCount(345)
                .status(ArticleStatus.PENDING)
                .submitCount(2)
                .lastSubmittedAt(LocalDateTime.parse("2026-03-26T18:40:00"))
                .build()));

        service.projectPendingTask(ArticleSubmittedEvent.builder()
                .eventId("article-submitted:51:1")
                .articleId(51L)
                .authorId(7L)
                .submitCount(2)
                .submittedAt(LocalDateTime.parse("2026-03-26T18:40:00"))
                .build());

        ArgumentCaptor<com.platform.common.dto.internal.ReviewTaskUpsertReq> captor =
                ArgumentCaptor.forClass(com.platform.common.dto.internal.ReviewTaskUpsertReq.class);
        verify(reviewTaskService).upsertTask(captor.capture());
        assertThat(captor.getValue().getArticleId()).isEqualTo(51L);
        assertThat(captor.getValue().getStatus()).isEqualTo(ArticleStatus.PENDING);
        assertThat(captor.getValue().getLastEventId()).isEqualTo("article-submitted:51:1");
    }

    @Test
    void articleSubmittedRemovesProjectionWhenSnapshotIsNotPending() {
        ReviewEventServiceImpl service = new ReviewEventServiceImpl(
                contentInternalClient,
                reviewTaskService,
                reviewLogMapper
        );

        when(contentInternalClient.reviewSnapshot(52L)).thenReturn(Result.ok(ArticleReviewSnapshotDto.builder()
                .articleId(52L)
                .authorId(8L)
                .status(ArticleStatus.DRAFT)
                .build()));

        service.projectPendingTask(ArticleSubmittedEvent.builder()
                .eventId("article-submitted:52:1")
                .articleId(52L)
                .authorId(8L)
                .build());

        verify(reviewTaskService).removeTask(any());
        verify(reviewTaskService, never()).upsertTask(any());
    }

    @Test
    void pendingToDraftStatusEventRemovesTaskAndWritesCancelLog() {
        ReviewEventServiceImpl service = new ReviewEventServiceImpl(
                contentInternalClient,
                reviewTaskService,
                reviewLogMapper
        );

        service.handleArticleStatusChanged(ArticleStatusChangedEvent.builder()
                .eventId("article-status:53:1")
                .articleId(53L)
                .authorId(9L)
                .fromStatus(ArticleStatus.PENDING)
                .toStatus(ArticleStatus.DRAFT)
                .build());

        ArgumentCaptor<ReviewLog> logCaptor = ArgumentCaptor.forClass(ReviewLog.class);
        verify(reviewTaskService).removeTask(any());
        verify(reviewLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getArticleId()).isEqualTo(53L);
        assertThat(logCaptor.getValue().getOperatorId()).isEqualTo(9L);
        assertThat(logCaptor.getValue().getAction()).isEqualTo(ReviewAction.CANCEL);
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo(ArticleStatus.PENDING);
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo(ArticleStatus.DRAFT);
    }
}
