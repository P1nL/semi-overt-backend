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

/**
 * Outbox 发布器公共支持类。
 * 负责批量读取可发布事件、发送到 RabbitMQ，并根据结果回写 outbox 状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherSupport {

    private final EventOutboxService eventOutboxService;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发布指定类型的待发送事件批次。
     * 每条事件独立处理，单条失败不会阻塞同批次内其他事件继续发送。
     */
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
                // 失败后不抛出到外层调度器，而是记录重试信息，交给下次批处理继续投递。
                log.warn("Publish outbox event failed: eventId={}, eventType={}, error={}",
                        eventOutbox.getEventId(), eventOutbox.getEventType(), ex.getMessage());
                eventOutboxService.markRetry(eventOutbox.getEventId(), ex.getMessage());
            }
        }
    }

    /**
     * 把 outbox 记录组装为 RabbitMQ 消息。
     * 消息头中附带事件 ID 和事件类型，便于消费端追踪。
     */
    private Message buildMessage(EventOutbox eventOutbox) {
        return MessageBuilder.withBody(eventOutbox.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("x-event-id", eventOutbox.getEventId())
                .setHeader("x-event-type", eventOutbox.getEventType())
                .build();
    }
}
