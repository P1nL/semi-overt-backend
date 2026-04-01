package com.platform.review.service;

import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ArticleSubmittedEvent;


public interface ReviewEventService {

    void projectPendingTask(ArticleSubmittedEvent event);

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}



