package com.platform.common.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.event.BaseDomainEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 事件监听执行器。
 * 统一封装 RabbitMQ 消费端的反序列化、消费幂等、失败登记、重试/死信处理与 ack 时机，
 * 让各业务监听器只关注具体事件的业务处理逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventListenerExecutor {

    private final ObjectMapper objectMapper;
    private final EventConsumeService eventConsumeService;
    private final RabbitRetrySupport rabbitRetrySupport;

    /**
     * 执行一次事件消费。
     * 处理顺序为：消息反序列化 -> 幂等检查 -> 标记开始消费 -> 业务处理 -> 标记成功。
     * 若任一步骤抛异常，则记录失败状态并交给重试/死信机制处理，最后统一 ack 当前消息。
     */
    public <T extends BaseDomainEvent> void execute(String consumer,
                                                    String eventType,
                                                    Message message,
                                                    Channel channel,
                                                    Class<T> eventClass,
                                                    ThrowingConsumer<T> handler) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        T event = null;
        try {
            event = objectMapper.readValue(message.getBody(), eventClass);
            if (eventConsumeService.isSuccess(event.getEventId(), consumer)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (!eventConsumeService.tryStart(event.getEventId(), consumer)) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            handler.accept(event);
            eventConsumeService.markSuccess(event.getEventId(), consumer);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            String eventId = event == null ? null : event.getEventId();
            if (eventId != null) {
                eventConsumeService.markFailed(eventId, consumer, ex.getMessage());
            }
            rabbitRetrySupport.retryOrDeadLetter(eventType, message, ex.getMessage());
            log.warn("Consume event failed: consumer={}, eventType={}, eventId={}, error={}",
                    consumer, eventType, eventId, ex.getMessage());
            channel.basicAck(deliveryTag, false);
        }
    }

    /**
     * 允许监听器传入可抛异常的业务处理函数。
     */
    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
