package com.platform.service;

import com.platform.common.event.ArticleStatusChangedEvent;

/**
 * 通知事件业务接口，定义对外暴露的服务能力。
 */

public interface NotificationEventService {

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}
