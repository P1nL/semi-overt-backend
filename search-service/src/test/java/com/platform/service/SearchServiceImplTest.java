package com.platform.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.platform.client.AuthInternalClient;
import com.platform.common.dto.internal.BatchUserQueryReq;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.document.ArticleSearchDocument;
import com.platform.dto.resp.ArticleCardResp;
import com.platform.dto.resp.SearchResp;
import com.platform.enums.DurationCategory;
import com.platform.service.impl.SearchServiceImpl;
import com.platform.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private AuthInternalClient authInternalClient;

    @Test
    void searchBuildsExpectedQueryAndMapsResults() {
        SearchServiceImpl service = new SearchServiceImpl(elasticsearchOperations, authInternalClient);
        ArticleSearchDocument document = ArticleSearchDocument.builder()
                .articleId(1001L)
                .authorId(9L)
                .title("cloud native search")
                .summary("summary for search")
                .coverUrl("/covers/1001.png")
                .coverColor("#102030")
                .readMinutes(new BigDecimal("6.5"))
                .durationCategory(DurationCategory.SHORT)
                .publishedAt(LocalDateTime.parse("2026-03-28T10:15:00"))
                .updatedAt(LocalDateTime.parse("2026-03-28T10:20:00"))
                .build();
        SearchHits<ArticleSearchDocument> hits = mockSearchHits(List.of(searchHit(document)), 11L);

        when(elasticsearchOperations.search(any(NativeQuery.class), org.mockito.ArgumentMatchers.eq(ArticleSearchDocument.class)))
                .thenReturn(hits);
        when(authInternalClient.batchUsers(any(BatchUserQueryReq.class))).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder()
                        .id(9L)
                        .username("searcher")
                        .nickname("检索作者")
                        .avatarUrl("/avatars/9.png")
                        .build()
        )));

        SearchResp response = service.search("cloud", 0, 99);

        ArgumentCaptor<NativeQuery> queryCaptor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), any());
        NativeQuery query = queryCaptor.getValue();

        assertThat(query.getPageable().getPageNumber()).isEqualTo(0);
        assertThat(query.getPageable().getPageSize()).isEqualTo(50);
        assertQuery(query.getQuery(), "cloud");
        assertThat(query.getSortOptions()).hasSize(2);
        assertThat(query.getSortOptions().get(0).isScore()).isTrue();
        assertThat(query.getSortOptions().get(0).score().order()).isEqualTo(SortOrder.Desc);
        assertThat(query.getSortOptions().get(1).isField()).isTrue();
        assertThat(query.getSortOptions().get(1).field().field()).isEqualTo("publishedAt");
        assertThat(query.getSortOptions().get(1).field().order()).isEqualTo(SortOrder.Desc);

        ArgumentCaptor<BatchUserQueryReq> batchReqCaptor = ArgumentCaptor.forClass(BatchUserQueryReq.class);
        verify(authInternalClient).batchUsers(batchReqCaptor.capture());
        assertThat(batchReqCaptor.getValue().getUserIds()).containsExactly(9L);

        assertThat(response.getKeyword()).isEqualTo("cloud");
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(50);
        assertThat(response.getTotal()).isEqualTo(11L);
        assertThat(response.getPages()).isEqualTo(1L);
        assertThat(response.getList()).hasSize(1);

        ArticleCardResp card = response.getList().get(0);
        assertThat(card.getArticleId()).isEqualTo(1001L);
        assertThat(card.getTitle()).isEqualTo("cloud native search");
        assertThat(card.getPreviewText()).isEqualTo("summary for search");
        assertThat(card.getAuthorId()).isEqualTo(9L);
        assertThat(card.getAuthorName()).isEqualTo("检索作者");
        assertThat(card.getAuthorAvatar()).isEqualTo("/avatars/9.png");
    }

    @Test
    void searchReturnsEmptyPageWithoutCallingAuthWhenNoHits() {
        SearchServiceImpl service = new SearchServiceImpl(elasticsearchOperations, authInternalClient);
        SearchHits<ArticleSearchDocument> hits = mockSearchHits(List.of(), 0L);

        when(elasticsearchOperations.search(any(NativeQuery.class), org.mockito.ArgumentMatchers.eq(ArticleSearchDocument.class)))
                .thenReturn(hits);

        SearchResp response = service.search("empty", 2, 5);

        assertThat(response.getKeyword()).isEqualTo("empty");
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getPageSize()).isEqualTo(5);
        assertThat(response.getTotal()).isZero();
        assertThat(response.getPages()).isZero();
        assertThat(response.getList()).isEmpty();
        verifyNoInteractions(authInternalClient);
    }

    @Test
    void searchFallsBackWhenAuthorEnrichmentFails() {
        SearchServiceImpl service = new SearchServiceImpl(elasticsearchOperations, authInternalClient);
        ArticleSearchDocument document = ArticleSearchDocument.builder()
                .articleId(1002L)
                .authorId(15L)
                .title("fallback search")
                .summary("summary for fallback")
                .publishedAt(LocalDateTime.parse("2026-03-28T11:00:00"))
                .updatedAt(LocalDateTime.parse("2026-03-28T11:00:30"))
                .build();
        SearchHits<ArticleSearchDocument> hits = mockSearchHits(List.of(searchHit(document)), 1L);

        when(elasticsearchOperations.search(any(NativeQuery.class), org.mockito.ArgumentMatchers.eq(ArticleSearchDocument.class)))
                .thenReturn(hits);
        when(authInternalClient.batchUsers(any(BatchUserQueryReq.class))).thenThrow(new RuntimeException("auth down"));

        SearchResp response = service.search("fallback", 1, 10);

        assertThat(response.getList()).hasSize(1);
        ArticleCardResp card = response.getList().get(0);
        assertThat(card.getArticleId()).isEqualTo(1002L);
        assertThat(card.getAuthorId()).isEqualTo(15L);
        assertThat(card.getAuthorName()).isNull();
        assertThat(card.getAuthorAvatar()).isNull();
    }

    private static void assertQuery(Query query, String keyword) {
        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(query.bool().should()).hasSize(2);
        assertThat(query.bool().should().get(0).match().field()).isEqualTo("title");
        assertThat(query.bool().should().get(0).match().query().stringValue()).isEqualTo(keyword);
        assertThat(query.bool().should().get(1).match().field()).isEqualTo("summary");
        assertThat(query.bool().should().get(1).match().query().stringValue()).isEqualTo(keyword);
    }

    private static SearchHits<ArticleSearchDocument> mockSearchHits(List<SearchHit<ArticleSearchDocument>> searchHits, long totalHits) {
        SearchHits<ArticleSearchDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(searchHits);
        when(hits.getTotalHits()).thenReturn(totalHits);
        return hits;
    }

    private static SearchHit<ArticleSearchDocument> searchHit(ArticleSearchDocument document) {
        return new SearchHit<>(
                "articles",
                document.getArticleId().toString(),
                null,
                1.0f,
                null,
                Map.of(),
                Map.of(),
                null,
                null,
                List.of(),
                document
        );
    }
}
