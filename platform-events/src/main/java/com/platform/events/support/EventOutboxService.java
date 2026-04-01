package com.platform.events.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.kernel.constant.EventConstants;
import com.platform.events.entity.EventOutbox;
import com.platform.events.enums.EventOutboxStatus;
import com.platform.kernel.event.BaseDomainEvent;
import com.platform.kernel.exception.BusinessException;
import com.platform.events.mapper.EventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 事件持久化服务。
 * 负责把领域事件先落到 event_outbox，再由独立发布器异步投递到 RabbitMQ，
 * 从而保证业务数据提交与事件发送之间的最终一致性。
 */
@Service
@RequiredArgsConstructor
public class EventOutboxService {

    private final EventOutboxMapper eventOutboxMapper;
    private final ObjectMapper objectMapper;

    /**
     * 保存一条待发布事件。
     * 调用方应在业务事务内调用该方法，使事件记录与主业务数据一同提交。
     */
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

    /**
     * 查询当前可发布的事件批次。
     * 只返回指定类型、状态为 PENDING 且已到重试时间的记录。
     */
    public List<EventOutbox> findPublishable(List<String> eventTypes, int batchSize) {
        return eventOutboxMapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .in(EventOutbox::getEventType, eventTypes)
                .eq(EventOutbox::getStatus, EventOutboxStatus.PENDING.name())
                .le(EventOutbox::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(EventOutbox::getCreatedAt)
                .last("LIMIT " + batchSize));
    }

    /**
     * 将事件标记为已发布。
     * 发布成功后清空错误信息并记录发布时间。
     */
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

    /**
     * 记录一次发布失败，并计算下次重试时间。
     * 超过最大重试次数后将事件置为 DEAD，后续不再自动投递。
     */
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

    /**
     * 根据事件类型解析出交换机、路由键等投递信息。
     */
    public EventConstants.EventRoute routeOf(String eventType) {
        return EventConstants.routeOf(eventType);
    }

    /**
     * 将领域事件序列化为 JSON 负载，供 outbox 持久化。
     */
    private String writePayload(BaseDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw BusinessException.serverError("Failed to serialize event payload");
        }
    }
}
