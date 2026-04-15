package com.platform.search.service;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.search.api.resp.ArticleCardResp;
import com.platform.search.api.resp.SearchResp;
import com.platform.kernel.enums.DurationCategory;
import com.platform.kernel.util.Result;
import com.platform.search.mapper.SearchArticleMapper;
import com.platform.search.model.SearchArticleRow;
import com.platform.search.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private SearchArticleMapper searchArticleMapper;

    @Mock
    private AuthUserQueryClient authInternalClient;

    @Test
    void searchNormalizesPageAndEscapesKeywordAndMapsResults() {
        SearchServiceImpl service = new SearchServiceImpl(searchArticleMapper, authInternalClient);
        SearchArticleRow row = buildRow(1001L, 9L, "cloud_native", "summary for search");
        row.setContent("## 搜索标题\n\n搜索正文");

        when(searchArticleMapper.countByKeyword("cloud\\_")).thenReturn(11L);
        when(searchArticleMapper.searchByKeyword("cloud\\_", 0, 50)).thenReturn(List.of(row));
        when(authInternalClient.batchUsers(any(BatchUserQueryReq.class))).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder()
                        .id(9L)
                        .username("searcher")
                        .nickname("Search Author")
                        .avatarUrl("/avatars/9.png")
                        .build()
        )));

        SearchResp response = service.search(" cloud_ ", 0, 99);

        verify(searchArticleMapper).countByKeyword("cloud\\_");
        verify(searchArticleMapper).searchByKeyword("cloud\\_", 0, 50);

        ArgumentCaptor<BatchUserQueryReq> batchReqCaptor = ArgumentCaptor.forClass(BatchUserQueryReq.class);
        verify(authInternalClient).batchUsers(batchReqCaptor.capture());
        assertThat(batchReqCaptor.getValue().getUserIds()).containsExactly(9L);

        assertThat(response.getKeyword()).isEqualTo("cloud_");
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(50);
        assertThat(response.getTotal()).isEqualTo(11L);
        assertThat(response.getPages()).isEqualTo(1L);
        assertThat(response.getList()).hasSize(1);

        ArticleCardResp card = response.getList().get(0);
        assertThat(card.getArticleId()).isEqualTo(1001L);
        assertThat(card.getTitle()).isEqualTo("cloud_native");
        assertThat(card.getPreviewText()).isEqualTo("搜索标题");
        assertThat(card.getAuthorId()).isEqualTo(9L);
        assertThat(card.getAuthorName()).isEqualTo("Search Author");
        assertThat(card.getAuthorAvatar()).isEqualTo("/avatars/9.png");
    }

    @Test
    void searchBuildsPreviewFromContentWhenSummaryIsEmpty() {
        SearchServiceImpl service = new SearchServiceImpl(searchArticleMapper, authInternalClient);
        SearchArticleRow row = buildRow(1003L, 18L, "heading only", null);
        row.setContent("## 富文本标题\n\n正文摘要");

        when(searchArticleMapper.countByKeyword("heading")).thenReturn(1L);
        when(searchArticleMapper.searchByKeyword("heading", 0, 10)).thenReturn(List.of(row));
        when(authInternalClient.batchUsers(any(BatchUserQueryReq.class))).thenReturn(Result.ok(List.of(
                UserSummaryDto.builder()
                        .id(18L)
                        .username("author18")
                        .nickname("Author 18")
                        .avatarUrl("/avatars/18.png")
                        .build()
        )));

        SearchResp response = service.search("heading", 1, 10);

        assertThat(response.getList()).hasSize(1);
        assertThat(response.getList().get(0).getSummary()).isNull();
        assertThat(response.getList().get(0).getPreviewText()).isEqualTo("富文本标题");
    }

    @Test
    void searchReturnsEmptyPageWithoutCallingAuthWhenNoHits() {
        SearchServiceImpl service = new SearchServiceImpl(searchArticleMapper, authInternalClient);
        when(searchArticleMapper.countByKeyword("empty")).thenReturn(0L);

        SearchResp response = service.search("empty", 2, 5);

        verify(searchArticleMapper).countByKeyword("empty");
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
        SearchServiceImpl service = new SearchServiceImpl(searchArticleMapper, authInternalClient);
        SearchArticleRow row = buildRow(1002L, 15L, "fallback search", "summary for fallback");

        when(searchArticleMapper.countByKeyword("fallback")).thenReturn(1L);
        when(searchArticleMapper.searchByKeyword("fallback", 0, 10)).thenReturn(List.of(row));
        when(authInternalClient.batchUsers(any(BatchUserQueryReq.class))).thenThrow(new RuntimeException("auth down"));

        SearchResp response = service.search("fallback", 1, 10);

        assertThat(response.getList()).hasSize(1);
        ArticleCardResp card = response.getList().get(0);
        assertThat(card.getArticleId()).isEqualTo(1002L);
        assertThat(card.getAuthorId()).isEqualTo(15L);
        assertThat(card.getAuthorName()).isNull();
        assertThat(card.getAuthorAvatar()).isNull();
    }

    @Test
    void searchSkipsMapperCallsWhenKeywordIsBlankAfterTrim() {
        SearchServiceImpl service = new SearchServiceImpl(searchArticleMapper, authInternalClient);

        SearchResp response = service.search("   ", 1, 10);

        assertThat(response.getKeyword()).isEmpty();
        assertThat(response.getTotal()).isZero();
        assertThat(response.getList()).isEmpty();
        verifyNoInteractions(searchArticleMapper, authInternalClient);
    }

    private static SearchArticleRow buildRow(long articleId, long authorId, String title, String summary) {
        SearchArticleRow row = new SearchArticleRow();
        row.setArticleId(articleId);
        row.setAuthorId(authorId);
        row.setTitle(title);
        row.setSummary(summary);
        row.setCoverUrl("/covers/" + articleId + ".png");
        row.setCoverColor("#102030");
        row.setReadMinutes(new BigDecimal("6.5"));
        row.setDurationCategory(DurationCategory.SHORT);
        row.setPublishedAt(LocalDateTime.parse("2026-03-28T10:15:00"));
        row.setUpdatedAt(LocalDateTime.parse("2026-03-28T10:20:00"));
        return row;
    }
}
