package com.platform.events.support;

import com.platform.kernel.constant.EventConstants;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Republishes a failed delivery to its queue-specific retry or dead-letter route with publisher confirms. */
@Component
public class RabbitRetrySupport {

    private final RabbitPublishConfirmSupport confirmedPublisher;
    private final int maxRetries;

    public RabbitRetrySupport(
            RabbitPublishConfirmSupport confirmedPublisher,
            @Value("${platform.events.consumer-max-retries:3}") int maxRetries) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("consumer max retries must not be negative");
        }
        this.confirmedPublisher = confirmedPublisher;
        this.maxRetries = maxRetries;
    }

    /** Returns only after the retry/DLQ publish is confirmed and not returned. */
    public void retryOrDeadLetter(String eventType, Message message, String errorMessage) {
        String queueName = message.getMessageProperties().getConsumerQueue();
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("consumer queue is required for retry routing");
        }
        int retryCount = currentRetryCount(message) + 1;
        Message nextMessage = MessageBuilder.fromClonedMessage(message)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("x-event-type", eventType)
                .setHeader("x-original-consumer-queue", queueName)
                .setHeader("x-event-retry-count", retryCount)
                .setHeader("x-last-error", truncate(errorMessage))
                .build();
        boolean deadLetter = retryCount > maxRetries;
        String exchange = deadLetter
                ? EventConstants.deadLetterExchangeOf(queueName)
                : EventConstants.retryExchangeOf(queueName);
        confirmedPublisher.sendConfirmed(
                exchange,
                queueName,
                nextMessage,
                "consumer:" + queueName + ":" + retryCount + ":" + UUID.randomUUID()
        );
    }

    int currentRetryCount(Message message) {
        Object header = message.getMessageProperties().getHeaders().get("x-event-retry-count");
        if (header instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (header instanceof String text) {
            try {
                return Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "unknown";
        }
        String singleLine = value.replace('\r', ' ').replace('\n', ' ');
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500);
    }
}