package com.platform.search.service;

import com.platform.search.service.impl.SearchIndexSyncServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatCode;

class SearchIndexSyncServiceImplTest {

    @Test
    void syncApprovedArticlesIsNoOp() {
        SearchIndexSyncServiceImpl service = new SearchIndexSyncServiceImpl();

        assertThatCode(service::syncApprovedArticles).doesNotThrowAnyException();
    }

    @Test
    void runDelegatesToNoOpSync() {
        SearchIndexSyncServiceImpl service = new SearchIndexSyncServiceImpl();

        assertThatCode(() -> service.run(new DefaultApplicationArguments(new String[0]))).doesNotThrowAnyException();
    }
}


