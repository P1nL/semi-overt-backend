package com.platform.events.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.kernel.event.BaseDomainEvent;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;

/** Deserializes, executes inbox+business work transactionally, then acknowledges only after commit. */
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
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Rabbit listener must not wrap EventListenerExecutor in an outer transaction");
        }
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        T event = null;
        boolean acknowledge = false;
        try {
            event = objectMapper.readValue(message.getBody(), eventClass);
            if (event.getEventId() == null || event.getEventId().isBlank()) {
                throw new IllegalArgumentException("eventId is required");
            }
            T handledEvent = event;
            eventConsumeService.executeInTransaction(
                    event.getEventId(),
                    consumer,
                    () -> handler.accept(handledEvent)
            );
            acknowledge = true;
        } catch (Exception processingFailure) {
            String eventId = event == null ? null : event.getEventId();
            try {
                rabbitRetrySupport.retryOrDeadLetter(eventType, message, processingFailure.getMessage());
                acknowledge = true;
                log.warn("Consume event failed and was confirmed to retry/DLQ: consumer={}, eventType={}, eventId={}, error={}",
                        consumer, eventType, eventId, processingFailure.getMessage());
            } catch (Exception retryPublishFailure) {
                log.error("Retry/DLQ publish failed; requeueing original delivery: consumer={}, eventType={}, eventId={}, error={}",
                        consumer, eventType, eventId, retryPublishFailure.getMessage());
                channel.basicNack(deliveryTag, false, true);
                return;
            }
        }

        // Deliberately outside the processing/retry catch. If ack fails after commit, the broker redelivers
        // and the SUCCESS inbox row absorbs it; no failure status or second retry message is created.
        if (acknowledge) {
            channel.basicAck(deliveryTag, false);
        }
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}