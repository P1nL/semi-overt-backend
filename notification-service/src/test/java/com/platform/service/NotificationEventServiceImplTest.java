package com.platform.service;

import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.entity.Notification;
import com.platform.entity.NotificationDelivery;
import com.platform.enums.ArticleStatus;
import com.platform.mapper.NotificationDeliveryMapper;
import com.platform.mapper.NotificationMapper;
import com.platform.service.impl.NotificationEventServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationDeliveryMapper notificationDeliveryMapper;

    @Test
    void approvedArticleCreatesNotificationAndTwoDeliveries() {
        NotificationEventServiceImpl service = new NotificationEventServiceImpl(
                notificationMapper,
                notificationDeliveryMapper
        );

        doAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(71L);
            return 1;
        }).when(notificationMapper).insert(any(Notification.class));

        service.handleArticleStatusChanged(ArticleStatusChangedEvent.builder()
                .eventId("article-status:71:1")
                .articleId(71L)
                .authorId(12L)
                .toStatus(ArticleStatus.APPROVED)
                .title("approved-article")
                .build());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        ArgumentCaptor<NotificationDelivery> deliveryCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);

        verify(notificationMapper).insert(notificationCaptor.capture());
        verify(notificationDeliveryMapper, org.mockito.Mockito.times(2)).insert(deliveryCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo(12L);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo("APPROVED");
        assertThat(deliveryCaptor.getAllValues()).extracting(NotificationDelivery::getChannel)
                .containsExactly("IN_APP", "EMAIL");
    }

    @Test
    void draftStatusDoesNotCreateNotification() {
        NotificationEventServiceImpl service = new NotificationEventServiceImpl(
                notificationMapper,
                notificationDeliveryMapper
        );

        service.handleArticleStatusChanged(ArticleStatusChangedEvent.builder()
                .eventId("article-status:72:1")
                .articleId(72L)
                .authorId(13L)
                .toStatus(ArticleStatus.DRAFT)
                .build());

        verify(notificationMapper, never()).insert(any());
        verify(notificationDeliveryMapper, never()).insert(any());
    }
}
