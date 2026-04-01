package com.platform.notification.mq;

import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.events.support.EventListenerExecutor;
import com.platform.notification.service.NotificationEventService;
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

