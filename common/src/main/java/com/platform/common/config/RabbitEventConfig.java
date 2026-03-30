package com.platform.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.EventConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ 事件配置类，负责声明领域事件所需的交换机、队列和绑定关系。
 */

@Configuration
public class RabbitEventConfig {

    /**
     * 注册当前配置类需要的 Bean。
     */
    @Bean
    public Declarables eventDeclarables(
            @Value("${platform.events.retry-delay-ms:30000}") long retryDelayMs
    ) {
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(new FanoutExchange(EventConstants.routeOf(EventConstants.ARTICLE_SUBMITTED).getExchange(), true, false));
        declarables.add(new FanoutExchange(EventConstants.routeOf(EventConstants.REVIEW_DECIDED).getExchange(), true, false));
        declarables.add(new FanoutExchange(EventConstants.routeOf(EventConstants.ARTICLE_STATUS_CHANGED).getExchange(), true, false));

        declarables.addAll(queueTopology(
                EventConstants.ARTICLE_SUBMITTED_QUEUE,
                EventConstants.routeOf(EventConstants.ARTICLE_SUBMITTED).getExchange(),
                retryDelayMs
        ));
        declarables.addAll(queueTopology(
                EventConstants.REVIEW_DECIDED_QUEUE,
                EventConstants.routeOf(EventConstants.REVIEW_DECIDED).getExchange(),
                retryDelayMs
        ));
        declarables.addAll(queueTopology(
                EventConstants.ARTICLE_STATUS_CHANGED_REVIEW_QUEUE,
                EventConstants.routeOf(EventConstants.ARTICLE_STATUS_CHANGED).getExchange(),
                retryDelayMs
        ));
        declarables.addAll(queueTopology(
                EventConstants.ARTICLE_STATUS_CHANGED_NOTIFICATION_QUEUE,
                EventConstants.routeOf(EventConstants.ARTICLE_STATUS_CHANGED).getExchange(),
                retryDelayMs
        ));
        declarables.addAll(queueTopology(
                EventConstants.ARTICLE_STATUS_CHANGED_SEARCH_QUEUE,
                EventConstants.routeOf(EventConstants.ARTICLE_STATUS_CHANGED).getExchange(),
                retryDelayMs
        ));
        return new Declarables(declarables);
    }

    /**
     * 注册当前配置类需要的 Bean。
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 注册当前配置类需要的 Bean。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(converter);
        return rabbitTemplate;
    }

    /**
     * 执行topology。
     */
    private List<Declarable> queueTopology(String queueName, String eventExchange, long retryDelayMs) {
        DirectExchange mainExchange = new DirectExchange(EventConstants.mainExchangeOf(queueName), true, false);
        DirectExchange retryExchange = new DirectExchange(EventConstants.retryExchangeOf(queueName), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(EventConstants.deadLetterExchangeOf(queueName), true, false);
        Queue mainQueue = new Queue(queueName, true);
        Queue retryQueue = new Queue(
                EventConstants.retryQueueOf(queueName),
                true,
                false,
                false,
                Map.of(
                        "x-message-ttl", retryDelayMs,
                        "x-dead-letter-exchange", EventConstants.mainExchangeOf(queueName),
                        "x-dead-letter-routing-key", queueName
                )
        );
        Queue deadLetterQueue = new Queue(EventConstants.deadLetterQueueOf(queueName), true);

        Binding eventBinding = BindingBuilder.bind(mainQueue).to(new FanoutExchange(eventExchange, true, false));
        Binding mainBinding = BindingBuilder.bind(mainQueue).to(mainExchange).with(queueName);
        Binding retryBinding = BindingBuilder.bind(retryQueue).to(retryExchange).with(queueName);
        Binding deadBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(queueName);

        return List.of(
                mainExchange,
                retryExchange,
                deadLetterExchange,
                mainQueue,
                retryQueue,
                deadLetterQueue,
                eventBinding,
                mainBinding,
                retryBinding,
                deadBinding
        );
    }
}
