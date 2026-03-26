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

@Component
@RequiredArgsConstructor
public class ReviewEventListener {

    private final EventListenerExecutor eventListenerExecutor;
    private final ReviewEventService reviewEventService;

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
