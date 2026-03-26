package com.platform.common.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.EventConstants;
import com.platform.common.entity.EventOutbox;
import com.platform.common.enums.EventOutboxStatus;
import com.platform.common.event.BaseDomainEvent;
import com.platform.exception.BusinessException;
import com.platform.mapper.EventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventOutboxService {

    private final EventOutboxMapper eventOutboxMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void saveEvent(String aggregateType,
                          String aggregateId,
                          String eventType,
                          BaseDomainEvent event) {
        if (event == null || event.getEventId() == null) {
            throw BusinessException.badRequest("eventId is required");
        }
        EventOutbox outbox = new EventOutbox();
        outbox.setEventId(event.getEventId());
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(writePayload(event));
        outbox.setStatus(EventOutboxStatus.PENDING.name());
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(LocalDateTime.now());
        eventOutboxMapper.insert(outbox);
    }

    public List<EventOutbox> findPublishable(List<String> eventTypes, int batchSize) {
        return eventOutboxMapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .in(EventOutbox::getEventType, eventTypes)
                .eq(EventOutbox::getStatus, EventOutboxStatus.PENDING.name())
                .le(EventOutbox::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(EventOutbox::getCreatedAt)
                .last("LIMIT " + batchSize));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markPublished(String eventId) {
        EventOutbox outbox = eventOutboxMapper.selectById(eventId);
        if (outbox == null) {
            return;
        }
        outbox.setStatus(EventOutboxStatus.PUBLISHED.name());
        outbox.setPublishedAt(LocalDateTime.now());
        outbox.setLastError(null);
        eventOutboxMapper.updateById(outbox);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRetry(String eventId, String errorMessage) {
        EventOutbox outbox = eventOutboxMapper.selectById(eventId);
        if (outbox == null) {
            return;
        }
        int retryCount = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        retryCount++;
        outbox.setRetryCount(retryCount);
        outbox.setLastError(errorMessage);
        if (retryCount >= 10) {
            outbox.setStatus(EventOutboxStatus.DEAD.name());
            outbox.setNextRetryAt(null);
        } else {
            outbox.setStatus(EventOutboxStatus.PENDING.name());
            outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(300, retryCount * 15L)));
        }
        eventOutboxMapper.updateById(outbox);
    }

    public EventConstants.EventRoute routeOf(String eventType) {
        return EventConstants.routeOf(eventType);
    }

    private String writePayload(BaseDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw BusinessException.serverError("Failed to serialize event payload");
        }
    }
}
