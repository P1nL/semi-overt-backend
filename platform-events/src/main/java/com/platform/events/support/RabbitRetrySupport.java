package com.platform.events.support;

import com.platform.kernel.constant.EventConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 消费重试支持类。
 * 负责根据当前重试次数，把失败消息重新投递到重试交换机或死信交换机。
 */
@Component
@RequiredArgsConstructor
public class RabbitRetrySupport {

    private final RabbitTemplate rabbitTemplate;

    @Value("${platform.events.consumer-max-retries:3}")
    private int maxRetries;

    /**
     * 根据重试次数决定后续投递方向。
     * 未超过阈值时进入重试队列；超过阈值后进入死信队列等待人工或离线处理。
     */
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

    /**
     * 从消息头中读取当前重试次数。
     * 没有重试头时按首次失败处理。
     */
    private int currentRetryCount(Message message) {
        Object header = message.getMessageProperties().getHeaders().get("x-event-retry-count");
        if (header instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
