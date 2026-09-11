package com.platform.notification.service;

import com.platform.notification.api.resp.NotificationResp;

import java.util.List;

public interface NotificationQueryService {

    List<NotificationResp> listForUser(Long userId, int limit);
}