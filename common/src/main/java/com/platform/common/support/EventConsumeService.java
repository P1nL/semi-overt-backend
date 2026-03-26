package com.platform.common.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.common.entity.EventConsumeLog;
import com.platform.common.enums.EventConsumeStatus;
import com.platform.exception.BusinessException;
import com.platform.mapper.EventConsumeLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EventConsumeService {

    private final EventConsumeLogMapper eventConsumeLogMapper;

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

    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(String eventId, String consumer) {
        updateStatus(eventId, consumer, EventConsumeStatus.SUCCESS, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String eventId, String consumer, String errorMessage) {
        updateStatus(eventId, consumer, EventConsumeStatus.FAILED, errorMessage);
    }

    public boolean isSuccess(String eventId, String consumer) {
        EventConsumeLog log = find(eventId, consumer);
        return log != null && EventConsumeStatus.SUCCESS.name().equals(log.getStatus());
    }

    private EventConsumeLog find(String eventId, String consumer) {
        if (eventId == null || consumer == null) {
            throw BusinessException.badRequest("eventId and consumer are required");
        }
        return eventConsumeLogMapper.selectOne(new LambdaQueryWrapper<EventConsumeLog>()
                .eq(EventConsumeLog::getEventId, eventId)
                .eq(EventConsumeLog::getConsumer, consumer)
                .last("LIMIT 1"));
    }

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
