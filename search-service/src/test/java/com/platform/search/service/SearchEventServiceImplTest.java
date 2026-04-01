package com.platform.search.service;

import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.search.service.impl.SearchEventServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class SearchEventServiceImplTest {

    @Test
    void handleArticleStatusChangedIsNoOp() {
        SearchEventServiceImpl service = new SearchEventServiceImpl();

        assertThatCode(() -> service.handleArticleStatusChanged(ArticleStatusChangedEvent.builder()
                .eventId("article-status:61:1")
                .articleId(61L)
                .authorId(5L)
                .fromStatus(ArticleStatus.PENDING)
                .toStatus(ArticleStatus.APPROVED)
                .title("approved-title")
                .summary("approved-summary")
                .build())).doesNotThrowAnyException();
    }
}


