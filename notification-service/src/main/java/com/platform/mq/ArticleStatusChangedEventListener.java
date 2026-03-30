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

/**
 * 文章状态变更事件监听器。
 * 从通知队列消费事件，并交给统一执行器处理幂等、异常和重试逻辑。
 */
@Component
@RequiredArgsConstructor
public class ArticleStatusChangedEventListener {

    private final EventListenerExecutor eventListenerExecutor;
    private final NotificationEventService notificationEventService;

    /**
     * 消费文章状态变更事件。
     */
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
