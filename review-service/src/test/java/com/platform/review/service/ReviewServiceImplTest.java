package com.platform.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.content.client.ContentReviewClient;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.api.PageResponse;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.events.support.EventOutboxService;
import com.platform.review.api.req.ReviewActionReq;
import com.platform.review.api.resp.ReviewActionResp;
import com.platform.review.api.resp.ReviewListItemResp;
import com.platform.review.entity.ReviewLog;
import com.platform.review.entity.ReviewTask;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.impl.ReviewServiceImpl;
import com.platform.kernel.util.Result;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initLambdaCache(ReviewTask.class, ReviewLog.class);
    }

    @Mock
    private ReviewTaskMapper reviewTaskMapper;

    @Mock
    private ReviewLogMapper reviewLogMapper;

    @Mock
    private AuthUserQueryClient authInternalClient;

    @Mock
    private ContentReviewClient contentInternalClient;

    @Mock
    private EventOutboxService eventOutboxService;

    @Test
    void pendingListReadsReviewTaskProjection() {
        ReviewServiceImpl service = new ReviewServiceImpl(
                reviewTaskMapper,
                reviewLogMapper,
                authInternalClient,
                contentInternalClient,
                eventOutboxService
        );

        ReviewTask task = new ReviewTask();
        task.setArticleId(21L);
        task.setAuthorId(9L);
        task.setTitle("pending-title");
        task.setWordCount(456);
        task.setSubmitCount(2);
        task.setSubmittedAt(LocalDateTime.parse("2026-03-26T09:30:00"));
        task.setStatus(ArticleStatus.PENDING);

        Page<ReviewTask> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(task));

        when(reviewTaskMapper.selectPage(any(), any())).thenReturn(page);
        when(contentInternalClient.reviewSnapshot(21L)).thenReturn(Result.ok(ArticleReviewSnapshotDto.builder()
                .articleId(21L)
                .authorId(9L)
                .status(ArticleStatus.PENDING)
                .build()));
        when(authInternalClient.batchUsers(any())).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder().id(9L).username("writer9").build()
        )));

        PageResponse<ReviewListItemResp> result = service.getPendingList(100L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getId()).isEqualTo(21L);
        assertThat(result.getList().get(0).getAuthor().getUsername()).isEqualTo("writer9");
    }

    @Test
    void pendingListRemovesStaleTaskWhenSnapshotMissing() {
        ReviewServiceImpl service = new ReviewServiceImpl(
                reviewTaskMapper,
                reviewLogMapper,
                authInternalClient,
                contentInternalClient,
                eventOutboxService
        );

        ReviewTask task = new ReviewTask();
        task.setArticleId(35L);
        task.setAuthorId(9L);
        task.setTitle("stale-title");
        task.setStatus(ArticleStatus.PENDING);

        Page<ReviewTask> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(task));

        when(reviewTaskMapper.selectPage(any(), any())).thenReturn(page);
        when(contentInternalClient.reviewSnapshot(35L)).thenReturn(Result.notFound("Article not found"));

        PageResponse<ReviewListItemResp> result = service.getPendingList(100L, 1, 10);

        assertThat(result.getList()).isEmpty();
        verify(reviewTaskMapper).delete(any());
    }

    @Test
    void pendingListRemovesTaskWhenSnapshotNoLongerPending() {
        ReviewServiceImpl service = new ReviewServiceImpl(
                reviewTaskMapper,
                reviewLogMapper,
                authInternalClient,
                contentInternalClient,
                eventOutboxService
        );

        ReviewTask task = new ReviewTask();
        task.setArticleId(36L);
        task.setAuthorId(9L);
        task.setTitle("returned-title");
        task.setStatus(ArticleStatus.PENDING);

        Page<ReviewTask> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(task));

        when(reviewTaskMapper.selectPage(any(), any())).thenReturn(page);
        when(contentInternalClient.reviewSnapshot(36L)).thenReturn(Result.ok(ArticleReviewSnapshotDto.builder()
                .articleId(36L)
                .authorId(9L)
                .status(ArticleStatus.RETURNED)
                .build()));

        PageResponse<ReviewListItemResp> result = service.getPendingList(100L, 1, 10);

        assertThat(result.getList()).isEmpty();
        verify(reviewTaskMapper).delete(any());
    }

    @Test
    void doReviewWritesReviewLogRemovesTaskAndAppliesContentResult() {
        ReviewServiceImpl service = new ReviewServiceImpl(
                reviewTaskMapper,
                reviewLogMapper,
                authInternalClient,
                contentInternalClient,
                eventOutboxService
        );

        when(contentInternalClient.reviewSnapshot(33L)).thenReturn(Result.ok(ArticleReviewSnapshotDto.builder()
                .articleId(33L)
                .authorId(7L)
                .status(ArticleStatus.PENDING)
                .build()));
        ReviewActionReq req = new ReviewActionReq();
        req.setAction("RETURN");
        req.setReason("needs references");

        ReviewActionResp resp = service.doReview(33L, 101L, req);

        ArgumentCaptor<ReviewLog> logCaptor = ArgumentCaptor.forClass(ReviewLog.class);

        assertThat(resp.getStatus()).isEqualTo(ArticleStatus.RETURNED);
        verify(reviewLogMapper).insert(logCaptor.capture());
        verify(reviewTaskMapper).delete(any());
        verify(eventOutboxService).saveEvent(any(), any(), any(), any(ReviewDecidedEvent.class));
        assertThat(logCaptor.getValue().getArticleId()).isEqualTo(33L);
        assertThat(logCaptor.getValue().getOperatorId()).isEqualTo(101L);
        assertThat(logCaptor.getValue().getAction()).isEqualTo(ReviewAction.RETURN);
        assertThat(logCaptor.getValue().getFromStatus()).isEqualTo(ArticleStatus.PENDING);
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo(ArticleStatus.RETURNED);
        assertThat(logCaptor.getValue().getReason()).isEqualTo("needs references");
    }

    @Test
    void doReviewRejectsMissingReasonForReturnAndReject() {
        ReviewServiceImpl service = new ReviewServiceImpl(
                reviewTaskMapper,
                reviewLogMapper,
                authInternalClient,
                contentInternalClient,
                eventOutboxService
        );

        ReviewActionReq req = new ReviewActionReq();
        req.setAction("REJECT");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.doReview(1L, 2L, req))
                .extracting("code")
                .isEqualTo(400);
    }
}




