package com.platform.service;

import com.platform.common.event.ArticleStatusChangedEvent;

public interface SearchEventService {

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}
