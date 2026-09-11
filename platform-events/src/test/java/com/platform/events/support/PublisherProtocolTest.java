package com.platform.events.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.events.entity.EventOutbox;
import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublisherProtocolTest {

    @Test
    void correlatedNackFailsPublish() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker nack"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        RabbitPublishConfirmSupport support = new RabbitPublishConfirmSupport(rabbit, 100);
        assertThrows(RabbitPublishConfirmSupport.RabbitPublishException.class,
                () -> support.sendConfirmed("exchange", "route", message("{}"), "nack"));
    }

    @Test
    void mandatoryReturnFailsPublishEvenWhenConfirmIsAck() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            Message returned = message("{}");
            correlation.setReturned(new ReturnedMessage(returned, 312, "NO_ROUTE", "exchange", "route"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        RabbitPublishConfirmSupport support = new RabbitPublishConfirmSupport(rabbit, 100);
        assertThrows(RabbitPublishConfirmSupport.RabbitPublishException.class,
                () -> support.sendConfirmed("exchange", "route", message("{}"), "returned"));
    }

    @Test
    void confirmTimeoutFailsPublish() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        RabbitPublishConfirmSupport support = new RabbitPublishConfirmSupport(rabbit, 20);
        assertThrows(RabbitPublishConfirmSupport.RabbitPublishException.class,
                () -> support.sendConfirmed("exchange", "route", message("{}"), "timeout"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publisher confirm nack", "mandatory return", "publisher confirm timed out"})
    void publisherFailureNeverMarksOutboxPublished(String failure) {
        EventOutboxService outbox = mock(EventOutboxService.class);
        RabbitPublishConfirmSupport confirmed = mock(RabbitPublishConfirmSupport.class);
        EventOutbox claim = claim();
        when(outbox.claimNextPublishable(anyString(), any(), anyString(), any()))
                .thenReturn(claim)
                .thenReturn(null);
        when(outbox.routeOf(EventConstants.ARTICLE_SUBMITTED))
                .thenReturn(EventConstants.routeOf(EventConstants.ARTICLE_SUBMITTED));
        when(outbox.markRetry(claim, failure)).thenReturn(true);
        doThrow(new RabbitPublishConfirmSupport.RabbitPublishException(failure))
                .when(confirmed).sendConfirmed(anyString(), anyString(), any(Message.class), anyString());

        new OutboxPublisherSupport(outbox, confirmed, "test-owner", 30_000)
                .publishPending("article", List.of(EventConstants.ARTICLE_SUBMITTED));

        verify(outbox, never()).markPublished(any());
        verify(outbox).markRetry(claim, failure);
    }

    @Test
    void listenerRejectsOuterTransactionBeforeProcessingOrAcknowledging() throws Exception {
        EventConsumeService inbox = mock(EventConsumeService.class);
        RabbitRetrySupport retry = mock(RabbitRetrySupport.class);
        Channel channel = mock(Channel.class);
        EventListenerExecutor executor = new EventListenerExecutor(new ObjectMapper(), inbox, retry);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(5L);
        properties.setConsumerQueue("random.queue");
        Message message = new Message("{\"eventId\":\"event-outer\"}".getBytes(), properties);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> executor.execute(
                    "consumer", EventConstants.ARTICLE_SUBMITTED, message, channel,
                    ArticleSubmittedEvent.class, ignored -> { }));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(inbox, retry, channel);
    }
    @Test
    void ackIoFailureAfterCommitDoesNotCreateRetryOrFailureState() throws Exception {
        EventConsumeService inbox = mock(EventConsumeService.class);
        RabbitRetrySupport retry = mock(RabbitRetrySupport.class);
        Channel channel = mock(Channel.class);
        doAnswer(invocation -> {
            EventConsumeService.ThrowingRunnable handler = invocation.getArgument(2);
            handler.run();
            return EventConsumeService.ConsumptionResult.HANDLED;
        }).when(inbox).executeInTransaction(anyString(), anyString(), any());
        doThrow(new IOException("ack connection lost")).when(channel).basicAck(7L, false);
        EventListenerExecutor executor = new EventListenerExecutor(new ObjectMapper(), inbox, retry);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        properties.setConsumerQueue("random.queue");
        Message message = new Message("{\"eventId\":\"event-ack\"}".getBytes(), properties);

        assertThrows(IOException.class, () -> executor.execute(
                "consumer", EventConstants.ARTICLE_SUBMITTED, message, channel,
                ArticleSubmittedEvent.class, ignored -> { }));
        verifyNoInteractions(retry);
    }

    private static EventOutbox claim() {
        EventOutbox outbox = new EventOutbox();
        outbox.setEventId("event-1");
        outbox.setAggregateType("article");
        outbox.setAggregateId("1");
        outbox.setEventType(EventConstants.ARTICLE_SUBMITTED);
        outbox.setPayload("{}");
        outbox.setLeaseOwner("test-owner");
        outbox.setLeaseToken("token");
        outbox.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
        return outbox;
    }

    private static Message message(String body) {
        return MessageBuilder.withBody(body.getBytes()).build();
    }
}