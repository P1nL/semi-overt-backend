package com.platform.kernel.constant;

import lombok.Getter;

import java.util.Map;

/**
 * 事件Constants常量定义，集中维护相关固定值。
 */

public final class EventConstants {

    public static final String ARTICLE_SUBMITTED = "ArticleSubmittedEvent";
    public static final String REVIEW_DECIDED = "ReviewDecidedEvent";
    public static final String ARTICLE_STATUS_CHANGED = "ArticleStatusChangedEvent";

    public static final String ARTICLE_SUBMITTED_QUEUE = "article.submitted.review";
    public static final String REVIEW_DECIDED_QUEUE = "review.decided.content";
    public static final String ARTICLE_STATUS_CHANGED_REVIEW_QUEUE = "article.status.changed.review";
    public static final String ARTICLE_STATUS_CHANGED_NOTIFICATION_QUEUE = "article.status.changed.notification";
    public static final String ARTICLE_STATUS_CHANGED_SEARCH_QUEUE = "article.status.changed.search";

    public static final String ARTICLE_SUBMITTED_CONSUMER = "review-service:article-submitted-task-projector";
    public static final String REVIEW_CANCEL_CONSUMER = "review-service:cancel-log-writer";
    public static final String REVIEW_DECIDED_CONSUMER = "content-service:review-result-applier";
    public static final String NOTIFICATION_CONSUMER = "notification-service:article-status-notifier";
    public static final String SEARCH_CONSUMER = "search-service:approved-article-indexer";

    public static final int DEFAULT_OUTBOX_BATCH_SIZE = 20;

    private static final Map<String, EventRoute> ROUTES = Map.of(
            ARTICLE_SUBMITTED, new EventRoute("article.submitted.exchange", "article.submitted"),
            REVIEW_DECIDED, new EventRoute("review.decided.exchange", "review.decided"),
            ARTICLE_STATUS_CHANGED, new EventRoute("article.status.changed.exchange", "article.status.changed")
    );

    private EventConstants() {
    }

    public static EventRoute routeOf(String eventType) {
        EventRoute route = ROUTES.get(eventType);
        if (route == null) {
            throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
        return route;
    }

    public static String retryExchangeOf(String queueName) {
        return queueName + ".retry.exchange";
    }

    public static String mainExchangeOf(String queueName) {
        return queueName + ".main.exchange";
    }

    public static String retryQueueOf(String queueName) {
        return queueName + ".retry";
    }

    public static String deadLetterExchangeOf(String queueName) {
        return queueName + ".dlq.exchange";
    }

    public static String deadLetterQueueOf(String queueName) {
        return queueName + ".dlq";
    }

    @Getter
    public static final class EventRoute {

        private final String exchange;
        private final String routingKey;

        private EventRoute(String exchange, String routingKey) {
            this.exchange = exchange;
            this.routingKey = routingKey;
        }
    }
}
