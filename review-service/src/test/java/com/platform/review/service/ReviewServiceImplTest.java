package com.platform.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.contract.content.client.ContentReviewClient;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.kernel.api.PageResponse;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.exception.BusinessException;
import com.platform.kernel.util.Result;
import com.platform.review.api.req.ReviewActionReq;
import com.platform.review.api.resp.ReviewActionResp;
import com.platform.review.api.resp.ReviewDecisionStatusResp;
import com.platform.review.api.resp.ReviewListItemResp;
import com.platform.review.entity.ReviewCommand;
import com.platform.review.entity.ReviewLog;
import com.platform.review.entity.ReviewTask;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.impl.ReviewDecisionCoordinatorImpl;
import com.platform.review.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initLambdaCache(ReviewTask.class, ReviewLog.class);
    }

    @Mock ReviewTaskMapper reviewTaskMapper;
    @Mock ReviewLogMapper reviewLogMapper;
    @Mock AuthUserQueryClient authUserQueryClient;
    @Mock ContentReviewClient contentReviewClient;
    @Mock ReviewDecisionCoordinator decisionCoordinator;

    private ReviewServiceImpl service() {
        return new ReviewServiceImpl(reviewTaskMapper, reviewLogMapper,
                authUserQueryClient, contentReviewClient, decisionCoordinator);
    }

    @Test
    void pendingListUsesAssignedAdminProjectionOnly() {
        ReviewTask task = new ReviewTask();
        task.setArticleId(21L);
        task.setAuthorId(9L);
        task.setAssignedAdminId(100L);
        task.setTitle("pending-title");
        task.setWordCount(456);
        task.setSubmitCount(2);
        task.setSubmittedAt(LocalDateTime.parse("2026-09-10T09:30:00"));
        task.setStatus(ArticleStatus.PENDING);
        Page<ReviewTask> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(task));
        when(reviewTaskMapper.selectPage(any(), any())).thenReturn(page);
        when(authUserQueryClient.batchUsers(any())).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder().id(9L).username("writer9").build())));

        PageResponse<ReviewListItemResp> result = service().getPendingList(100L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(21L);
            assertThat(item.getAuthor().getUsername()).isEqualTo("writer9");
        });
        verify(contentReviewClient, never()).reviewSnapshot(any());
    }

    @Test
    void finalCommandReturnsOnlyAfterContentFinalAndForwardsBaseline() {
        ReviewCommand claimed = command("decision-33", ReviewDecisionCoordinatorImpl.PROCESSING,
                null, null);
        ReviewCommand finalized = command("decision-33", ReviewDecisionCoordinatorImpl.FINAL,
                ArticleStatus.RETURNED, 13L);
        finalized.setUpdatedAt(LocalDateTime.parse("2026-09-10T10:05:00"));
        when(decisionCoordinator.claim(33L, 101L, "decision-33", "RETURN", "needs references",
                "submission-33", 12L)).thenReturn(claimed);
        when(contentReviewClient.applyReviewResult(any(), any())).thenReturn(Result.ok(
                ReviewDecisionResultDto.builder()
                        .decisionId("decision-33").articleId(33L).submissionId("submission-33")
                        .state("FINAL").status(ArticleStatus.RETURNED).version(13L)
                        .updatedAt(finalized.getUpdatedAt()).adminId(101L)
                        .action(ReviewAction.RETURN).reason("needs references").build()));
        when(decisionCoordinator.finalizeAuthoritativeResult(any())).thenReturn(finalized);
        ReviewActionReq req = request("RETURN", "needs references", "decision-33", "submission-33", 12L);

        ReviewActionResp response = service().doReview(33L, 101L, "decision-33", req);

        assertThat(response.getState()).isEqualTo("FINAL");
        assertThat(response.getStatus()).isEqualTo(ArticleStatus.RETURNED);
        assertThat(response.getDecisionId()).isEqualTo("decision-33");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(
                com.platform.contract.content.dto.ApplyReviewResultReq.class);
        verify(contentReviewClient).applyReviewResult(org.mockito.ArgumentMatchers.eq(33L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getSubmissionId()).isEqualTo("submission-33");
        assertThat(requestCaptor.getValue().getExpectedVersion()).isEqualTo(12L);
    }

    @Test
    void authorityFailureIs503WithRetainedDecisionId() {
        ReviewCommand claimed = command("decision-timeout", ReviewDecisionCoordinatorImpl.PROCESSING,
                null, null);
        when(decisionCoordinator.claim(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(claimed);
        when(contentReviewClient.applyReviewResult(any(), any()))
                .thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service().doReview(33L, 101L, "decision-timeout",
                request("APPROVE", null, null, "submission-33", 12L)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(503);
                    assertThat(ex.getDetails()).isEqualTo(Map.of(
                            "decisionId", "decision-timeout", "state", "PROCESSING"));
                });
    }

    @Test
    void sameHeaderAndBodyDecisionIdMustMatch() {
        assertThatThrownBy(() -> service().doReview(1L, 2L, "header-key",
                request("APPROVE", null, "body-key", "submission", 1L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(409));
        verify(decisionCoordinator, never()).claim(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void decisionStatusIsPrivateToCommandAssignee() {
        ReviewCommand command = command("decision-status", ReviewDecisionCoordinatorImpl.PROCESSING,
                null, null);
        when(decisionCoordinator.find("decision-status")).thenReturn(command);

        ReviewDecisionStatusResp status = service().getDecisionStatus(33L, 101L, "decision-status");
        assertThat(status.getState()).isEqualTo("PROCESSING");

        assertThatThrownBy(() -> service().getDecisionStatus(33L, 102L, "decision-status"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(403));
    }

    private ReviewCommand command(String decisionId, String state, ArticleStatus status, Long version) {
        ReviewCommand command = new ReviewCommand();
        command.setDecisionId(decisionId);
        command.setArticleId(33L);
        command.setSubmissionId("submission-33");
        command.setExpectedVersion(12L);
        command.setAdminId(101L);
        command.setAction(ReviewAction.RETURN);
        command.setReason("needs references");
        command.setState(state);
        command.setStatus(status);
        command.setArticleVersion(version);
        command.setUpdatedAt(LocalDateTime.parse("2026-09-10T10:00:00"));
        return command;
    }

    private ReviewActionReq request(String action, String reason, String decisionId,
                                    String submissionId, Long expectedVersion) {
        ReviewActionReq req = new ReviewActionReq();
        req.setAction(action);
        req.setReason(reason);
        req.setDecisionId(decisionId);
        req.setSubmissionId(submissionId);
        req.setExpectedVersion(expectedVersion);
        return req;
    }
}
