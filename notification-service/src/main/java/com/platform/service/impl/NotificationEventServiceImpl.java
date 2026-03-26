package com.platform.service.impl;

import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.entity.Notification;
import com.platform.entity.NotificationDelivery;
import com.platform.enums.ArticleStatus;
import com.platform.mapper.NotificationDeliveryMapper;
import com.platform.mapper.NotificationMapper;
import com.platform.service.NotificationEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationEventServiceImpl implements NotificationEventService {

    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryMapper notificationDeliveryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleArticleStatusChanged(ArticleStatusChangedEvent event) {
        if (event.getToStatus() != ArticleStatus.APPROVED
                && event.getToStatus() != ArticleStatus.RETURNED
                && event.getToStatus() != ArticleStatus.REJECTED) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(event.getAuthorId());
        notification.setType(event.getToStatus().name());
        notification.setTitle(buildTitle(event.getToStatus()));
        notification.setContent(buildContent(event));
        notification.setBizId(event.getArticleId());
        notification.setReadStatus(Boolean.FALSE);
        notificationMapper.insert(notification);

        NotificationDelivery inAppDelivery = new NotificationDelivery();
        inAppDelivery.setNotificationId(notification.getId());
        inAppDelivery.setChannel("IN_APP");
        inAppDelivery.setStatus("SENT");
        inAppDelivery.setRetryCount(0);
        inAppDelivery.setSentAt(LocalDateTime.now());
        notificationDeliveryMapper.insert(inAppDelivery);

        NotificationDelivery emailDelivery = new NotificationDelivery();
        emailDelivery.setNotificationId(notification.getId());
        emailDelivery.setChannel("EMAIL");
        emailDelivery.setStatus("PENDING");
        emailDelivery.setRetryCount(0);
        notificationDeliveryMapper.insert(emailDelivery);
    }

    private String buildTitle(ArticleStatus status) {
        return switch (status) {
            case APPROVED -> "Review approved";
            case RETURNED -> "Review returned";
            case REJECTED -> "Review rejected";
            default -> "Article status updated";
        };
    }

    private String buildContent(ArticleStatusChangedEvent event) {
        String title = event.getTitle() == null || event.getTitle().isBlank()
                ? "Untitled article"
                : event.getTitle();
        return switch (event.getToStatus()) {
            case APPROVED -> "\"" + title + "\" has been approved and published.";
            case RETURNED -> "\"" + title + "\" was returned for revision.";
            case REJECTED -> "\"" + title + "\" did not pass review.";
            default -> "\"" + title + "\" status changed.";
        };
    }
}
