package com.platform.service;

import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.common.event.ArticleSubmittedEvent;

public interface ReviewEventService {

    void projectPendingTask(ArticleSubmittedEvent event);

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}
