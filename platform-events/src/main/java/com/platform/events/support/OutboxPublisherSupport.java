package com.platform.events.support;

import com.platform.events.entity.EventOutbox;
import com.platform.kernel.constant.EventConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Claims rows, publishes outside the DB transaction, then performs fenced state updates. */
@Slf4j
@Service
public class OutboxPublisherSupport {

    private final EventOutboxService eventOutboxService;
    private final RabbitPublishConfirmSupport confirmedPublisher;
    private final String publisherOwner;
    private final Duration leaseDuration;
    private final AtomicBoolean publishing = new AtomicBoolean();

    public OutboxPublisherSupport(
            EventOutboxService eventOutboxService,
            RabbitPublishConfirmSupport confirmedPublisher,
            @Value("${platform.events.publisher-owner:}") String configuredOwner,
            @Value("${platform.events.outbox-lease-ms:30000}") long leaseMs) {
        if (leaseMs <= 0) {
            throw new IllegalArgumentException("outbox lease must be positive");
        }
        this.eventOutboxService = eventOutboxService;
        this.confirmedPublisher = confirmedPublisher;
        this.publisherOwner = configuredOwner == null || configuredOwner.isBlank()
                ? defaultOwner()
                : configuredOwner;
        if (publisherOwner.length() > 128) {
            throw new IllegalArgumentException("publisher owner exceeds 128 characters");
        }
        this.leaseDuration = Duration.ofMillis(leaseMs);
    }

    /** Source-compatible entry point for current article/review publishers. */
    public void publishPending(List<String> eventTypes) {
        publishPending(inferAggregateType(eventTypes), eventTypes);
    }

    /** Explicit producer namespace prevents a service from claiming another domain's rows. */
    public void publishPending(String aggregateType, List<String> eventTypes) {
        if (!publishing.compareAndSet(false, true)) {
            log.debug("Skip overlapping outbox publish invocation: owner={}", publisherOwner);
            return;
        }
        try {
            for (int sent = 0; sent < EventConstants.DEFAULT_OUTBOX_BATCH_SIZE; sent++) {
                EventOutbox eventOutbox = eventOutboxService.claimNextPublishable(
                        aggregateType,
                        eventTypes,
                        publisherOwner,
                        leaseDuration
                );
                if (eventOutbox == null) {
                    break;
                }
                if (!publishClaim(eventOutbox)) {
                    break;
                }
            }
        } finally {
            publishing.set(false);
        }
    }

    boolean publishClaim(EventOutbox eventOutbox) {
        EventConstants.EventRoute route = eventOutboxService.routeOf(eventOutbox.getEventType());
        return publishClaim(eventOutbox, route.getExchange(), route.getRoutingKey());
    }

    boolean publishClaim(EventOutbox eventOutbox, String exchange, String routingKey) {
        try {
            confirmedPublisher.sendConfirmed(
                    exchange,
                    routingKey,
                    buildMessage(eventOutbox),
                    "outbox:" + eventOutbox.getEventId() + ":" + eventOutbox.getLeaseToken()
            );
            if (!eventOutboxService.markPublished(eventOutbox)) {
                log.info("Ignore stale outbox confirm after lease loss: eventId={}, owner={}",
                        eventOutbox.getEventId(), eventOutbox.getLeaseOwner());
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("Publish outbox event failed: eventId={}, eventType={}, error={}",
                    eventOutbox.getEventId(), eventOutbox.getEventType(), ex.getMessage());
            if (!eventOutboxService.markRetry(eventOutbox, ex.getMessage())) {
                log.info("Ignore stale outbox failure after lease loss: eventId={}, owner={}",
                        eventOutbox.getEventId(), eventOutbox.getLeaseOwner());
                return false;
            }
            return true;
        }
    }

    private Message buildMessage(EventOutbox eventOutbox) {
        return MessageBuilder.withBody(eventOutbox.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("x-event-id", eventOutbox.getEventId())
                .setHeader("x-event-type", eventOutbox.getEventType())
                .build();
    }

    private static String inferAggregateType(List<String> eventTypes) {
        if (eventTypes != null && !eventTypes.isEmpty()
                && eventTypes.stream().allMatch(EventConstants.REVIEW_DECIDED::equals)) {
            return "review";
        }
        if (eventTypes != null && !eventTypes.isEmpty()
                && eventTypes.stream().allMatch(type -> EventConstants.ARTICLE_SUBMITTED.equals(type)
                || EventConstants.ARTICLE_STATUS_CHANGED.equals(type))) {
            return "article";
        }
        throw new IllegalArgumentException("publisher must provide an explicit aggregateType for these eventTypes");
    }

    private static String defaultOwner() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            host = "unknown-host";
        }
        String owner = host + ":" + UUID.randomUUID();
        return owner.length() <= 128 ? owner : owner.substring(0, 128);
    }
}