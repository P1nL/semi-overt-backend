package com.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.client.AuthInternalClient;
import com.platform.client.ContentInternalClient;
import com.platform.common.api.PageResponse;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.common.event.ReviewDecidedEvent;
import com.platform.common.support.EventOutboxService;
import com.platform.dto.req.ReviewActionReq;
import com.platform.dto.resp.ReviewActionResp;
import com.platform.dto.resp.ReviewListItemResp;
import com.platform.entity.ReviewLog;
import com.platform.entity.ReviewTask;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import com.platform.mapper.ReviewLogMapper;
import com.platform.mapper.ReviewTaskMapper;
import com.platform.service.impl.ReviewServiceImpl;
import com.platform.util.Result;
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

/**
 * 审核服务ImplTest业务接口，定义对外暴露的服务能力。
 */

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
    private AuthInternalClient authInternalClient;

    @Mock
    private ContentInternalClient contentInternalClient;

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
