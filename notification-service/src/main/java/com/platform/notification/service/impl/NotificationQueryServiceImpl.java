package com.platform.notification.service.impl;

import com.platform.kernel.exception.BusinessException;
import com.platform.notification.api.resp.NotificationResp;
import com.platform.notification.mapper.NotificationMapper;
import com.platform.notification.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {

    static final int MAX_LIMIT = 50;

    private final NotificationMapper notificationMapper;

    @Override
    public List<NotificationResp> listForUser(Long userId, int limit) {
        if (userId == null) {
            throw BusinessException.unauthorized("Authentication required");
        }
        return notificationMapper.findByUserId(userId, normalizeLimit(limit));
    }

    static int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}