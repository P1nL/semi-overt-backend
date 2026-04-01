package com.platform.search.service.impl;

import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.search.service.SearchEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.search.index", name = "enabled", havingValue = "true")
public class SearchEventServiceImpl implements SearchEventService {

        @Override
    public void handleArticleStatusChanged(ArticleStatusChangedEvent event) {
        log.info("Search index chain enabled but no-op: articleId={}, toStatus={}",
                event.getArticleId(), event.getToStatus());
    }
}


