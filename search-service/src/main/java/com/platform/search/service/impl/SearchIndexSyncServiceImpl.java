package com.platform.search.service.impl;

import com.platform.search.service.SearchIndexSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.index-sync.enabled", havingValue = "true", matchIfMissing = false)
public class SearchIndexSyncServiceImpl implements SearchIndexSyncService, ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        syncApprovedArticles();
    }

    @Override
    public void syncApprovedArticles() {
        log.info("Search index sync is disabled for MySQL search mode");
    }
}
