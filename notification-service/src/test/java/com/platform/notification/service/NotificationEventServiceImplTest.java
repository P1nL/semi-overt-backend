package com.platform.notification.service;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.notification.entity.Notification;
import com.platform.notification.entity.NotificationDelivery;
import com.platform.notification.mapper.NotificationDeliveryMapper;
import com.platform.notification.mapper.NotificationMapper;
import com.platform.notification.service.impl.NotificationEventServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationDeliveryMapper notificationDeliveryMapper;

    @Test
    void approvedArticleUsesSourceNotificationSemanticAndKeepsEmailPending() {
        NotificationEventServiceImpl service = new NotificationEventServiceImpl(
                notificationMapper,
                notificationDeliveryMapper
        );

        doAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(71L);
            return 1;
        }).when(notificationMapper).insert(any(Notification.class));

        service.handleArticleStatusChanged(accepted(ReviewAction.APPROVE, null));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        ArgumentCaptor<NotificationDelivery> deliveryCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);

        verify(notificationMapper).insert(notificationCaptor.capture());
        verify(notificationDeliveryMapper, org.mockito.Mockito.times(2)).insert(deliveryCaptor.capture());

        Notification notification = notificationCaptor.getValue();
        assertThat(notification.getUserId()).isEqualTo(12L);
        assertThat(notification.getType()).isEqualTo("ARTICLE_REVIEW");
        assertThat(notification.getTitle()).isEqualTo("文章审核结果");
        assertThat(notification.getContent()).contains("通过审核");
        assertThat(notification.getDecisionId()).isEqualTo("decision-71");

        NotificationDelivery inApp = deliveryCaptor.getAllValues().stream()
                .filter(delivery -> "IN_APP".equals(delivery.getChannel()))
                .findFirst().orElseThrow();
        NotificationDelivery email = deliveryCaptor.getAllValues().stream()
                .filter(delivery -> "EMAIL".equals(delivery.getChannel()))
                .findFirst().orElseThrow();
        assertThat(inApp.getStatus()).isEqualTo("SENT");
        assertThat(inApp.getSentAt()).isNotNull();
        assertThat(email.getStatus()).isEqualTo("PENDING");
        assertThat(email.getSentAt()).isNull();
    }

    @Test
    void returnedOrRejectedReasonIsBoundedBeforeInsert() {
        NotificationEventServiceImpl service = new NotificationEventServiceImpl(
                notificationMapper,
                notificationDeliveryMapper
        );
        doAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(72L);
            return 1;
        }).when(notificationMapper).insert(any(Notification.class));

        String longReason = "拒绝原因".repeat(300);
        service.handleArticleStatusChanged(accepted(ReviewAction.REJECT, longReason));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("ARTICLE_REVIEW");
        assertThat(captor.getValue().getTitle()).isEqualTo("文章审核结果");
        assertThat(captor.getValue().getContent()).contains("未通过审核");
        assertThat(captor.getValue().getContent().codePointCount(0, captor.getValue().getContent().length()))
                .isLessThanOrEqualTo(500);
        assertThat(captor.getValue().getContent()).endsWith("…");
    }

    @Test
    void duplicateDecisionWithSamePayloadDoesNotCreateSecondDeliveryPair() {
        NotificationEventServiceImpl service = new NotificationEventServiceImpl(
                notificationMapper,
                notificationDeliveryMapper
        );
        Notification existing = new Notification();
        existing.setUserId(12L);
        existing.setType("ARTICLE_REVIEW");
        existing.setTitle("文章审核结果");
        existing.setContent("你的文章已通过审核：reviewed article");
        existing.setBizId(71L);
        when(notificationMapper.findByDecisionId("decision-71")).thenReturn(existing);
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("duplicate decision"));

        service.handleArticleStatusChanged(accepted(ReviewAction.APPROVE, null));

        verify(notificationMapper).findByDecisionId("decision-71");
        verify(notificationDeliveryMapper, never()).insert(any(NotificationDelivery.class));
    }

    @Test
    void mismatchedConfirmedActionCannotCreateNotification() {
        NotificationEventServiceImpl service = new NotificationEventServiceImpl(
                notificationMapper,
                notificationDeliveryMapper
        );

        ArticleStatusChangedEvent event = accepted(ReviewAction.RETURN, null);
        event.setToStatus(ArticleStatus.APPROVED);

        assertThrows(IllegalArgumentException.class,
                () -> service.handleArticleStatusChanged(event));
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void unconfirmedDeletedAndLegacyEventsDoNotNotify() {
        NotificationEventServiceImpl service = new NotificationEventServiceImpl(
                notificationMapper,
                notificationDeliveryMapper
        );

        ArticleStatusChangedEvent missingDecision = accepted(ReviewAction.APPROVE, null);
        missingDecision.setDecisionId(null);
        service.handleArticleStatusChanged(missingDecision);

        ArticleStatusChangedEvent deleted = accepted(ReviewAction.APPROVE, null);
        deleted.setDeleted(true);
        service.handleArticleStatusChanged(deleted);

        ArticleStatusChangedEvent legacy = ArticleStatusChangedEvent.builder()
                .articleId(73L)
                .authorId(14L)
                .toStatus(ArticleStatus.APPROVED)
                .build();
        service.handleArticleStatusChanged(legacy);

        verify(notificationMapper, never()).insert(any(Notification.class));
        verify(notificationDeliveryMapper, never()).insert(any(NotificationDelivery.class));
    }

    private ArticleStatusChangedEvent accepted(ReviewAction action, String reason) {
        return ArticleStatusChangedEvent.builder()
                .eventId("article-status:71:1")
                .articleId(71L)
                .authorId(12L)
                .fromStatus(ArticleStatus.PENDING)
                .toStatus(switch (action) {
                    case APPROVE -> ArticleStatus.APPROVED;
                    case RETURN -> ArticleStatus.RETURNED;
                    case REJECT -> ArticleStatus.REJECTED;
                    case CANCEL -> ArticleStatus.DRAFT;
                })
                .decisionId("decision-71")
                .submissionId("submission-71")
                .articleVersion(3L)
                .adminId(99L)
                .action(action)
                .reason(reason)
                .title("reviewed article")
                .build();
    }
}