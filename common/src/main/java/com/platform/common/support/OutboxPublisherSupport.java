package com.platform.common.support;

import com.platform.common.constant.EventConstants;
import com.platform.common.entity.EventOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherSupport {

    private final EventOutboxService eventOutboxService;
    private final RabbitTemplate rabbitTemplate;

    public void publishPending(List<String> eventTypes) {
        List<EventOutbox> batch = eventOutboxService.findPublishable(
                eventTypes,
                EventConstants.DEFAULT_OUTBOX_BATCH_SIZE
        );
        for (EventOutbox eventOutbox : batch) {
            EventConstants.EventRoute route = eventOutboxService.routeOf(eventOutbox.getEventType());
            try {
                rabbitTemplate.send(route.getExchange(), "", buildMessage(eventOutbox));
                eventOutboxService.markPublished(eventOutbox.getEventId());
            } catch (Exception ex) {
                log.warn("Publish outbox event failed: eventId={}, eventType={}, error={}",
                        eventOutbox.getEventId(), eventOutbox.getEventType(), ex.getMessage());
                eventOutboxService.markRetry(eventOutbox.getEventId(), ex.getMessage());
            }
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
}
