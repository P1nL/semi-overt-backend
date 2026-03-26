package com.platform.common.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.event.BaseDomainEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventListenerExecutor {

    private final ObjectMapper objectMapper;
    private final EventConsumeService eventConsumeService;
    private final RabbitRetrySupport rabbitRetrySupport;

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

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
