package com.platform.search.service;

import com.platform.kernel.event.ArticleStatusChangedEvent;


public interface SearchEventService {

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}


