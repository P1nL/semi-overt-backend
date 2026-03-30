package com.platform.common.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.common.entity.EventConsumeLog;
import com.platform.common.enums.EventConsumeStatus;
import com.platform.exception.BusinessException;
import com.platform.common.mapper.EventConsumeLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 事件消费记录服务。
 * 负责维护 `event_consume_log` 中的消费状态，用于消费端幂等控制和失败追踪。
 */
@Service
@RequiredArgsConstructor
public class EventConsumeService {

    private final EventConsumeLogMapper eventConsumeLogMapper;

    /**
     * 尝试开始一次事件消费。
     * 若该 consumer 对同一 eventId 已成功消费，则返回 false；否则返回 true 允许继续处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean tryStart(String eventId, String consumer) {
        EventConsumeLog existing = find(eventId, consumer);
        if (existing != null) {
            return existing.getStatus() != EventConsumeStatus.SUCCESS.name();
        }

        EventConsumeLog log = new EventConsumeLog();
        log.setEventId(eventId);
        log.setConsumer(consumer);
        log.setStatus(EventConsumeStatus.PROCESSING.name());
        try {
            eventConsumeLogMapper.insert(log);
            return true;
        } catch (DuplicateKeyException ex) {
            EventConsumeLog reloaded = find(eventId, consumer);
            return reloaded == null || reloaded.getStatus() != EventConsumeStatus.SUCCESS.name();
        }
    }

    /**
     * 将消费记录标记为成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(String eventId, String consumer) {
        updateStatus(eventId, consumer, EventConsumeStatus.SUCCESS, null);
    }

    /**
     * 将消费记录标记为失败，并记录错误信息。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String eventId, String consumer, String errorMessage) {
        updateStatus(eventId, consumer, EventConsumeStatus.FAILED, errorMessage);
    }

    /**
     * 判断某个 consumer 是否已经成功消费过该事件。
     */
    public boolean isSuccess(String eventId, String consumer) {
        EventConsumeLog log = find(eventId, consumer);
        return log != null && EventConsumeStatus.SUCCESS.name().equals(log.getStatus());
    }

    /**
     * 查询事件消费记录。
     * eventId 和 consumer 缺失时直接视为调用方错误。
     */
    private EventConsumeLog find(String eventId, String consumer) {
        if (eventId == null || consumer == null) {
            throw BusinessException.badRequest("eventId and consumer are required");
        }
        return eventConsumeLogMapper.selectOne(new LambdaQueryWrapper<EventConsumeLog>()
                .eq(EventConsumeLog::getEventId, eventId)
                .eq(EventConsumeLog::getConsumer, consumer)
                .last("LIMIT 1"));
    }

    /**
     * 更新消费状态。
     * 若记录不存在则补建一条，以兼容“先标记结果、后补日志”的异常路径。
     */
    private void updateStatus(String eventId,
                              String consumer,
                              EventConsumeStatus status,
                              String errorMessage) {
        EventConsumeLog existing = find(eventId, consumer);
        if (existing == null) {
            existing = new EventConsumeLog();
            existing.setEventId(eventId);
            existing.setConsumer(consumer);
            existing.setCreatedAt(LocalDateTime.now());
            eventConsumeLogMapper.insert(existing);
        }
        existing.setStatus(status.name());
        existing.setErrorMessage(errorMessage);
        existing.setConsumedAt(status == EventConsumeStatus.SUCCESS ? LocalDateTime.now() : null);
        eventConsumeLogMapper.updateById(existing);
    }
}
