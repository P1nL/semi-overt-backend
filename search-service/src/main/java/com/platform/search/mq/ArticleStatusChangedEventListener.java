package com.platform.search.mq;

import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.events.support.EventListenerExecutor;
import com.platform.search.service.SearchEventService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.search.index", name = "enabled", havingValue = "true")
public class ArticleStatusChangedEventListener {

    private final EventListenerExecutor eventListenerExecutor;
    private final SearchEventService searchEventService;

        @RabbitListener(queues = EventConstants.ARTICLE_STATUS_CHANGED_SEARCH_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        eventListenerExecutor.execute(
                EventConstants.SEARCH_CONSUMER,
                EventConstants.ARTICLE_STATUS_CHANGED,
                message,
                channel,
                ArticleStatusChangedEvent.class,
                searchEventService::handleArticleStatusChanged
        );
    }
}


