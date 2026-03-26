package com.platform.common.support;

import com.platform.common.constant.EventConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitRetrySupport {

    private final RabbitTemplate rabbitTemplate;

    @Value("${platform.events.consumer-max-retries:3}")
    private int maxRetries;

    public void retryOrDeadLetter(String eventType, Message message, String errorMessage) {
        String queueName = message.getMessageProperties().getConsumerQueue();
        int retryCount = currentRetryCount(message) + 1;
        Message nextMessage = MessageBuilder.fromMessage(message)
                .setHeader("x-event-retry-count", retryCount)
                .setHeader("x-last-error", errorMessage)
                .build();
        if (retryCount > maxRetries) {
            rabbitTemplate.send(EventConstants.deadLetterExchangeOf(queueName), queueName, nextMessage);
            return;
        }
        rabbitTemplate.send(EventConstants.retryExchangeOf(queueName), queueName, nextMessage);
    }

    private int currentRetryCount(Message message) {
        Object header = message.getMessageProperties().getHeaders().get("x-event-retry-count");
        if (header instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
