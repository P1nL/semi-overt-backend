package com.platform.content.service;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.kernel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleReviewEventFlowTest {
    @Mock ArticleMapper articleMapper;
    @Mock AuthUserQueryClient authInternalClient;
    @Mock ReviewReasonClient reviewInternalClient;
    @Mock ReviewTaskClient reviewTaskInternalClient;
    @Mock EventOutboxService eventOutboxService;
    @Mock ReviewDecisionService reviewDecisionService;

    @Test
    void legacyUnversionedEventFailsClosed() {
        ReviewDecidedEvent legacy = ReviewDecidedEvent.builder()
                .eventId("event").articleId(41L).adminId(100L).action(ReviewAction.APPROVE).build();
        assertThatThrownBy(() -> service().applyReviewDecisionEvent(legacy))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
    }

    @Test
    void versionedEventUsesSameAuthoritativeApplyPath() {
        ReviewDecidedEvent event = ReviewDecidedEvent.builder()
                .eventId("event").articleId(41L).decisionId("decision-1")
                .submissionId("submission-1").expectedVersion(5L)
                .adminId(100L).action(ReviewAction.APPROVE).reason(null).build();
        when(reviewDecisionService.apply(org.mockito.ArgumentMatchers.eq(41L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(ReviewDecisionResultDto.builder().state("FINAL").build());

        service().applyReviewDecisionEvent(event);

        ArgumentCaptor<ApplyReviewResultReq> request = ArgumentCaptor.forClass(ApplyReviewResultReq.class);
        verify(reviewDecisionService).apply(org.mockito.ArgumentMatchers.eq(41L), request.capture());
        assertThat(request.getValue().getDecisionId()).isEqualTo("decision-1");
        assertThat(request.getValue().getExpectedVersion()).isEqualTo(5L);
    }

    private ArticleServiceImpl service() {
        return new ArticleServiceImpl(articleMapper, authInternalClient, reviewInternalClient,
                reviewTaskInternalClient, eventOutboxService, reviewDecisionService);
    }
}
