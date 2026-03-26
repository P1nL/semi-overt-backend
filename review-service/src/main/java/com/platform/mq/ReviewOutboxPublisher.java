package com.platform.mq;

import com.platform.common.constant.EventConstants;
import com.platform.common.support.OutboxPublisherSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ReviewOutboxPublisher {

    private final OutboxPublisherSupport outboxPublisherSupport;

    @Scheduled(fixedDelayString = "${platform.events.outbox-publish-delay-ms:5000}")
    public void publishPending() {
        outboxPublisherSupport.publishPending(List.of(EventConstants.REVIEW_DECIDED));
    }
}
