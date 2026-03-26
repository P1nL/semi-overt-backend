package com.platform.service;

import com.platform.common.event.ArticleStatusChangedEvent;

public interface NotificationEventService {

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}
