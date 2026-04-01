package com.platform.content.mq;

import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.events.support.EventListenerExecutor;
import com.platform.content.service.ArticleService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
@RequiredArgsConstructor
public class ReviewDecidedEventListener {

    private final EventListenerExecutor eventListenerExecutor;
    private final ArticleService articleService;

        @RabbitListener(queues = EventConstants.REVIEW_DECIDED_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        eventListenerExecutor.execute(
                EventConstants.REVIEW_DECIDED_CONSUMER,
                EventConstants.REVIEW_DECIDED,
                message,
                channel,
                ReviewDecidedEvent.class,
                articleService::applyReviewDecisionEvent
        );
    }
}



