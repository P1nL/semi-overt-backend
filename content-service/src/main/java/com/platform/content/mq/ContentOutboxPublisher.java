package com.platform.content.mq;

import com.platform.kernel.constant.EventConstants;
import com.platform.events.support.OutboxPublisherSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@RequiredArgsConstructor
public class ContentOutboxPublisher {

    private final OutboxPublisherSupport outboxPublisherSupport;

        @Scheduled(fixedDelayString = "${platform.events.outbox-publish-delay-ms:5000}")
    public void publishPending() {
        outboxPublisherSupport.publishPending(List.of(
                EventConstants.ARTICLE_SUBMITTED,
                EventConstants.ARTICLE_STATUS_CHANGED
        ));
    }
}



