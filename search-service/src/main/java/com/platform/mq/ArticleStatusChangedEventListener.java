package com.platform.mq;

import com.platform.common.constant.EventConstants;
import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.common.support.EventListenerExecutor;
import com.platform.service.SearchEventService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 搜索索引文章状态变更监听器。
 * 负责从搜索队列消费事件，并交由统一事件执行器处理幂等与异常逻辑。
 */
@Component
@RequiredArgsConstructor
public class ArticleStatusChangedEventListener {

    private final EventListenerExecutor eventListenerExecutor;
    private final SearchEventService searchEventService;

    /**
     * 消费文章状态变更事件。
     */
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
