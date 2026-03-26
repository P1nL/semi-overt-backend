package com.platform.mq;

import com.platform.common.constant.EventConstants;
import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.common.support.EventListenerExecutor;
import com.platform.service.NotificationEventService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ArticleStatusChangedEventListener {

    private final EventListenerExecutor eventListenerExecutor;
    private final NotificationEventService notificationEventService;

    @RabbitListener(queues = EventConstants.ARTICLE_STATUS_CHANGED_NOTIFICATION_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        eventListenerExecutor.execute(
                EventConstants.NOTIFICATION_CONSUMER,
                EventConstants.ARTICLE_STATUS_CHANGED,
                message,
                channel,
                ArticleStatusChangedEvent.class,
                notificationEventService::handleArticleStatusChanged
        );
    }
}
