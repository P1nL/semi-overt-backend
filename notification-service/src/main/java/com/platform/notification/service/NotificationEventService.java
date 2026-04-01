package com.platform.notification.service;

import com.platform.kernel.event.ArticleStatusChangedEvent;


public interface NotificationEventService {

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}

