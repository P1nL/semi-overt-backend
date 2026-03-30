package com.platform.mq;

import com.platform.common.constant.EventConstants;
import com.platform.common.support.OutboxPublisherSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审核Outbox消息发布组件，负责扫描并投递待发送的领域事件。
 */

@Component
@RequiredArgsConstructor
public class ReviewOutboxPublisher {

    private final OutboxPublisherSupport outboxPublisherSupport;

    /**
     * 按调度策略执行定时处理。
     */
    @Scheduled(fixedDelayString = "${platform.events.outbox-publish-delay-ms:5000}")
    public void publishPending() {
        outboxPublisherSupport.publishPending(List.of(EventConstants.REVIEW_DECIDED));
    }
}
