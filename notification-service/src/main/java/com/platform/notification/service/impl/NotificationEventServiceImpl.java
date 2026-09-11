package com.platform.notification.service.impl;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.notification.entity.Notification;
import com.platform.notification.entity.NotificationDelivery;
import com.platform.notification.mapper.NotificationDeliveryMapper;
import com.platform.notification.mapper.NotificationMapper;
import com.platform.notification.service.NotificationEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationEventServiceImpl implements NotificationEventService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final String TRUNCATION_MARK = "…";

    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryMapper notificationDeliveryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleArticleStatusChanged(ArticleStatusChangedEvent event) {
        // Only the content authority's accepted, versioned result can notify.
        // Delete/cancel/unconfirmed/legacy events must not create notifications.
        if (event == null
                || Boolean.TRUE.equals(event.getDeleted())
                || event.getDecisionId() == null || event.getDecisionId().isBlank()
                || event.getSubmissionId() == null || event.getSubmissionId().isBlank()
                || event.getArticleVersion() == null
                || event.getAdminId() == null
                || event.getAction() == null
                || event.getFromStatus() != ArticleStatus.PENDING
                || event.getAuthorId() == null
                || event.getArticleId() == null) {
            return;
        }
        if (event.getToStatus() != ArticleStatus.APPROVED
                && event.getToStatus() != ArticleStatus.RETURNED
                && event.getToStatus() != ArticleStatus.REJECTED) {
            return;
        }

        ArticleStatus expected = switch (event.getAction()) {
            case APPROVE -> ArticleStatus.APPROVED;
            case RETURN -> ArticleStatus.RETURNED;
            case REJECT -> ArticleStatus.REJECTED;
            case CANCEL -> null;
        };
        if (expected != event.getToStatus()) {
            throw new IllegalArgumentException("Mismatched confirmed review action/status");
        }

        Notification notification = new Notification();
        notification.setUserId(event.getAuthorId());
        // This is the source/monolith notification semantic. Historical rows
        // are read back as stored; only newly materialized rows use this value.
        notification.setType("ARTICLE_REVIEW");
        notification.setTitle("文章审核结果");
        notification.setContent(buildContent(event));
        notification.setBizId(event.getArticleId());
        notification.setReadStatus(Boolean.FALSE);
        notification.setDecisionId(event.getDecisionId());
        notification.setCreatedAt(LocalDateTime.now());

        // decision_id is the durable final guard even when distinct event IDs
        // replay the same confirmed result.
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException duplicate) {
            Notification existing = notificationMapper.findByDecisionId(event.getDecisionId());
            if (existing != null) {
                if (!sameDecisionOrKnownLegacyS3Payload(existing, notification, event)) {
                    throw new IllegalStateException("Notification decision payload mismatch");
                }
                // Idempotent replay: do not rewrite the historical row and do
                // not create another delivery pair.
                return;
            }
            throw duplicate;
        }

        NotificationDelivery inAppDelivery = new NotificationDelivery();
        inAppDelivery.setNotificationId(notification.getId());
        inAppDelivery.setChannel("IN_APP");
        inAppDelivery.setStatus("SENT");
        inAppDelivery.setRetryCount(0);
        inAppDelivery.setSentAt(LocalDateTime.now());
        inAppDelivery.setCreatedAt(LocalDateTime.now());
        notificationDeliveryMapper.insert(inAppDelivery);

        // This is only a pending delivery intent. There is no email worker in
        // this service, so PENDING must not be reported or changed to SENT.
        NotificationDelivery emailDelivery = new NotificationDelivery();
        emailDelivery.setNotificationId(notification.getId());
        emailDelivery.setChannel("EMAIL");
        emailDelivery.setStatus("PENDING");
        emailDelivery.setRetryCount(0);
        emailDelivery.setCreatedAt(LocalDateTime.now());
        notificationDeliveryMapper.insert(emailDelivery);
    }

    private boolean sameDecisionOrKnownLegacyS3Payload(Notification existing,
                                                       Notification expected,
                                                       ArticleStatusChangedEvent event) {
        if (!Objects.equals(existing.getUserId(), expected.getUserId())
                || !Objects.equals(existing.getBizId(), expected.getBizId())) {
            return false;
        }

        // A replay of the current contract is an exact payload match.
        if (Objects.equals(existing.getType(), expected.getType())
                && Objects.equals(existing.getTitle(), expected.getTitle())
                && Objects.equals(existing.getContent(), expected.getContent())) {
            return true;
        }

        // S3 initially wrote English status-specific notifications before the
        // source-compatible ARTICLE_REVIEW semantic was restored. Recognize
        // only that known shape, and never rewrite its stored text.
        return matchesKnownS3Notification(existing, event);
    }

    private boolean matchesKnownS3Notification(Notification existing,
                                                ArticleStatusChangedEvent event) {
        String title = event.getTitle() == null || event.getTitle().isBlank()
                ? "Untitled article"
                : event.getTitle();
        String expectedType = event.getToStatus().name();
        String expectedTitle = switch (event.getToStatus()) {
            case APPROVED -> "Review approved";
            case RETURNED -> "Review returned";
            case REJECTED -> "Review rejected";
            default -> null;
        };
        String expectedContent = switch (event.getToStatus()) {
            case APPROVED -> "\"" + title + "\" has been approved and published.";
            case RETURNED -> "\"" + title + "\" was returned for revision.";
            case REJECTED -> "\"" + title + "\" did not pass review.";
            default -> null;
        };
        return Objects.equals(existing.getType(), expectedType)
                && Objects.equals(existing.getTitle(), expectedTitle)
                && Objects.equals(existing.getContent(), expectedContent);
    }

    private String buildContent(ArticleStatusChangedEvent event) {
        String title = event.getTitle() == null ? "" : event.getTitle();
        String reason = event.getReason() == null ? "" : event.getReason();
        // Match the monolith buildNotification contract: approval includes the
        // article title; return/reject preserve the review reason in the same
        // non-approved shape.
        String content = event.getAction() == ReviewAction.APPROVE
                ? "你的文章已通过审核：" + title
                : "你的文章未通过审核：" + reason;
        return boundedContent(content);
    }

    /**
     * V1/V3 keep content at VARCHAR(500). Bound only newly built notifications
     * before persistence; this never rewrites or truncates historical rows.
     */
    static String boundedContent(String content) {
        if (content == null || content.codePointCount(0, content.length()) <= MAX_CONTENT_LENGTH) {
            return content;
        }
        int keepCodePoints = MAX_CONTENT_LENGTH - TRUNCATION_MARK.codePointCount(0, TRUNCATION_MARK.length());
        int end = content.offsetByCodePoints(0, keepCodePoints);
        return content.substring(0, end) + TRUNCATION_MARK;
    }
}