package com.platform.mq;

import com.platform.common.constant.EventConstants;
import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.common.event.ArticleSubmittedEvent;
import com.platform.common.support.EventListenerExecutor;
import com.platform.service.ReviewEventService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 审核事件消息监听器，负责消费队列中的领域事件并委托业务服务处理。
 */

@Component
@RequiredArgsConstructor
public class ReviewEventListener {

    private final EventListenerExecutor eventListenerExecutor;
    private final ReviewEventService reviewEventService;

    /**
     * 消费队列中的消息并交给业务服务处理。
     */
    @RabbitListener(queues = EventConstants.ARTICLE_SUBMITTED_QUEUE)
    public void onArticleSubmitted(Message message, Channel channel) throws IOException {
        eventListenerExecutor.execute(
                EventConstants.ARTICLE_SUBMITTED_CONSUMER,
                EventConstants.ARTICLE_SUBMITTED,
                message,
                channel,
                ArticleSubmittedEvent.class,
                reviewEventService::projectPendingTask
        );
    }

    /**
     * 消费队列中的消息并交给业务服务处理。
     */
    @RabbitListener(queues = EventConstants.ARTICLE_STATUS_CHANGED_REVIEW_QUEUE)
    public void onArticleStatusChanged(Message message, Channel channel) throws IOException {
        eventListenerExecutor.execute(
                EventConstants.REVIEW_CANCEL_CONSUMER,
                EventConstants.ARTICLE_STATUS_CHANGED,
                message,
                channel,
                ArticleStatusChangedEvent.class,
                reviewEventService::handleArticleStatusChanged
        );
    }
}
