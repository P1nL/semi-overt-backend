package com.platform.service;

import com.platform.document.ArticleSearchDocument;
import com.platform.enums.DurationCategory;
import com.platform.mapper.SearchIndexMapper;
import com.platform.repository.ArticleSearchRepository;
import com.platform.service.impl.SearchIndexSyncServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexSyncServiceImplTest {

    @Mock
    private SearchIndexMapper searchIndexMapper;

    @Mock
    private ArticleSearchRepository articleSearchRepository;

    @Test
    void syncApprovedArticlesIndexesApprovedDocumentsAndRemovesStaleOnes() {
        SearchIndexSyncServiceImpl service = new SearchIndexSyncServiceImpl(searchIndexMapper, articleSearchRepository);
        ArticleSearchDocument approved = ArticleSearchDocument.builder()
                .articleId(1L)
                .authorId(2L)
                .title("Demo Quick Start: Distributed Refactor")
                .summary("A seeded QUICK article for the local demo home page.")
                .readMinutes(new BigDecimal("4.0"))
                .durationCategory(DurationCategory.QUICK)
                .publishedAt(LocalDateTime.parse("2026-03-23T15:29:03"))
                .updatedAt(LocalDateTime.parse("2026-03-23T15:29:03"))
                .build();
        ArticleSearchDocument stale = ArticleSearchDocument.builder()
                .articleId(99L)
                .title("stale")
                .build();

        when(searchIndexMapper.selectApprovedArticlesForIndex()).thenReturn(List.of(approved));
        when(articleSearchRepository.findAll()).thenReturn(List.of(approved, stale));

        service.syncApprovedArticles();

        ArgumentCaptor<Iterable<ArticleSearchDocument>> saveCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(articleSearchRepository).saveAll(saveCaptor.capture());
        assertThat(saveCaptor.getValue()).containsExactly(approved);
        verify(articleSearchRepository).deleteAllById(List.of(99L));
    }

    @Test
    void syncApprovedArticlesSkipsDeletionWhenIndexAlreadyMatchesApprovedSet() {
        SearchIndexSyncServiceImpl service = new SearchIndexSyncServiceImpl(searchIndexMapper, articleSearchRepository);
        ArticleSearchDocument approved = ArticleSearchDocument.builder()
                .articleId(5L)
                .title("Smoke Article")
                .build();

        when(searchIndexMapper.selectApprovedArticlesForIndex()).thenReturn(List.of(approved));
        when(articleSearchRepository.findAll()).thenReturn(List.of(approved));

        service.syncApprovedArticles();

        verify(articleSearchRepository).saveAll(List.of(approved));
        verify(articleSearchRepository).findAll();
    }
}
