package com.platform.events.support;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Sends one mandatory Rabbit message and returns only after its correlated confirm is final. */
@Component
public class RabbitPublishConfirmSupport {

    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public RabbitPublishConfirmSupport(
            RabbitTemplate rabbitTemplate,
            @Value("${platform.events.publisher-confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        if (confirmTimeoutMs <= 0) {
            throw new IllegalArgumentException("publisher confirm timeout must be positive");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setMandatory(true);
        this.confirmTimeout = Duration.ofMillis(confirmTimeoutMs);
    }

    public void sendConfirmed(String exchange,
                              String routingKey,
                              Message message,
                              String correlationId) {
        Objects.requireNonNull(message, "message");
        CorrelationData correlation = new CorrelationData(correlationId);
        rabbitTemplate.send(exchange, routingKey, message, correlation);

        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new RabbitPublishException("publisher confirm timed out", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RabbitPublishException("publisher confirm interrupted", ex);
        } catch (ExecutionException ex) {
            throw new RabbitPublishException("publisher confirm failed: " + safeMessage(ex.getCause()), ex);
        }

        if (!confirm.isAck()) {
            throw new RabbitPublishException("publisher confirm nack: " + safeMessage(confirm.getReason()));
        }
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new RabbitPublishException("mandatory return: replyCode=" + returned.getReplyCode()
                    + ", exchange=" + returned.getExchange()
                    + ", routingKey=" + returned.getRoutingKey());
        }
    }

    private static String safeMessage(Object value) {
        if (value == null) {
            return "unknown";
        }
        String text = String.valueOf(value);
        return text.length() <= 400 ? text : text.substring(0, 400);
    }

    public static class RabbitPublishException extends RuntimeException {
        public RabbitPublishException(String message) {
            super(message);
        }

        public RabbitPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}