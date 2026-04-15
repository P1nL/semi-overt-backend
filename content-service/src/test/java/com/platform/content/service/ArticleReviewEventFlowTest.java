package com.platform.content.service;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.events.support.EventOutboxService;
import com.platform.content.entity.Article;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.HomeService;
import com.platform.content.service.impl.ArticleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ArticleReviewEventFlowTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AuthUserQueryClient authInternalClient;

    @Mock
    private ReviewReasonClient reviewInternalClient;

    @Mock
    private ReviewTaskClient reviewTaskInternalClient;

    @Mock
    private EventOutboxService eventOutboxService;

    @Mock
    private HomeService homeService;

    @Test
    void reviewDecidedEventAppliesArticleStateAndPublishesStatusChanged() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper,
                redisTemplate,
                authInternalClient,
                reviewInternalClient,
                reviewTaskInternalClient,
                eventOutboxService,
                homeService
        );

        Article article = new Article();
        article.setId(41L);
        article.setAuthorId(8L);
        article.setStatus(ArticleStatus.PENDING);
        article.setTitle("event-title");
        article.setSummary("event-summary");

        when(articleMapper.selectById(41L)).thenReturn(article);

        ReviewDecidedEvent event = ReviewDecidedEvent.builder()
                .eventId("review-decided:41:1")
                .articleId(41L)
                .adminId(100L)
                .action(ReviewAction.APPROVE)
                .fromStatus(ArticleStatus.PENDING)
                .toStatus(ArticleStatus.APPROVED)
                .reviewedAt(LocalDateTime.parse("2026-03-26T18:30:00"))
                .build();

        service.applyReviewDecisionEvent(event);

        ArgumentCaptor<ArticleStatusChangedEvent> statusCaptor = ArgumentCaptor.forClass(ArticleStatusChangedEvent.class);

        assertThat(article.getStatus()).isEqualTo(ArticleStatus.APPROVED);
        assertThat(article.getPublishedAt()).isEqualTo(LocalDateTime.parse("2026-03-26T18:30:00"));
        verify(articleMapper).updateById(article);
        verify(eventOutboxService).saveEvent(any(), any(), any(), statusCaptor.capture());
        assertThat(statusCaptor.getValue().getArticleId()).isEqualTo(41L);
        assertThat(statusCaptor.getValue().getFromStatus()).isEqualTo(ArticleStatus.PENDING);
        assertThat(statusCaptor.getValue().getToStatus()).isEqualTo(ArticleStatus.APPROVED);
    }

    @Test
    void duplicateReviewDecidedEventWithSameTargetStatusIsIgnored() {
        ArticleServiceImpl service = new ArticleServiceImpl(
                articleMapper,
                redisTemplate,
                authInternalClient,
                reviewInternalClient,
                reviewTaskInternalClient,
                eventOutboxService,
                homeService
        );

        Article article = new Article();
        article.setId(42L);
        article.setAuthorId(9L);
        article.setStatus(ArticleStatus.RETURNED);

        when(articleMapper.selectById(42L)).thenReturn(article);

        ReviewDecidedEvent event = ReviewDecidedEvent.builder()
                .eventId("review-decided:42:1")
                .articleId(42L)
                .adminId(101L)
                .action(ReviewAction.RETURN)
                .fromStatus(ArticleStatus.PENDING)
                .toStatus(ArticleStatus.RETURNED)
                .reviewedAt(LocalDateTime.parse("2026-03-26T18:31:00"))
                .build();

        service.applyReviewDecisionEvent(event);

        verify(articleMapper, never()).updateById(any());
        verify(eventOutboxService, never()).saveEvent(any(), any(), any(), any());
    }
}



