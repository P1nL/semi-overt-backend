package com.platform.service;

import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.document.ArticleSearchDocument;
import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import com.platform.repository.ArticleSearchRepository;
import com.platform.service.impl.SearchEventServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchEventServiceImplTest {

    @Mock
    private ArticleSearchRepository articleSearchRepository;

    @Test
    void approvedArticleIsIndexed() {
        SearchEventServiceImpl service = new SearchEventServiceImpl(articleSearchRepository);

        service.handleArticleStatusChanged(ArticleStatusChangedEvent.builder()
                .eventId("article-status:61:1")
                .articleId(61L)
                .authorId(5L)
                .fromStatus(ArticleStatus.PENDING)
                .toStatus(ArticleStatus.APPROVED)
                .title("approved-title")
                .summary("approved-summary")
                .coverUrl("/covers/61.png")
                .coverColor("#112233")
                .readMinutes(new BigDecimal("3.5"))
                .durationCategory(DurationCategory.SHORT)
                .publishedAt(LocalDateTime.parse("2026-03-26T18:50:00"))
                .updatedAt(LocalDateTime.parse("2026-03-26T18:50:10"))
                .build());

        ArgumentCaptor<ArticleSearchDocument> captor = ArgumentCaptor.forClass(ArticleSearchDocument.class);
        verify(articleSearchRepository).save(captor.capture());
        assertThat(captor.getValue().getArticleId()).isEqualTo(61L);
        assertThat(captor.getValue().getTitle()).isEqualTo("approved-title");
        assertThat(captor.getValue().getDurationCategory()).isEqualTo(DurationCategory.SHORT);
    }

    @Test
    void nonApprovedArticleRemovesIndexDocument() {
        SearchEventServiceImpl service = new SearchEventServiceImpl(articleSearchRepository);

        service.handleArticleStatusChanged(ArticleStatusChangedEvent.builder()
                .eventId("article-status:62:1")
                .articleId(62L)
                .authorId(6L)
                .fromStatus(ArticleStatus.APPROVED)
                .toStatus(ArticleStatus.RETURNED)
                .build());

        verify(articleSearchRepository).deleteById(62L);
    }
}
