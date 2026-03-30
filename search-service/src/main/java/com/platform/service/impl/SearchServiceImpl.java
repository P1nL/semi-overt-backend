package com.platform.service.impl;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.platform.client.AuthInternalClient;
import com.platform.common.api.ResultUtils;
import com.platform.common.dto.internal.BatchUserQueryReq;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.document.ArticleSearchDocument;
import com.platform.dto.resp.ArticleCardResp;
import com.platform.dto.resp.SearchResp;
import com.platform.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搜索服务实现。
 * 基于 Elasticsearch 中的文章投影文档提供公开搜索，只返回已进入搜索索引的公开文章。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ElasticsearchOperations elasticsearchOperations;
    private final AuthInternalClient authInternalClient;

    /**
     * 搜索文章。
     * 查询范围只覆盖标题和摘要，分页参数会被修正到安全范围内。
     */
    @Override
    public SearchResp search(String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        NativeQuery query = buildSearchQuery(keyword, safePage, safePageSize);

        SearchHits<ArticleSearchDocument> hits = elasticsearchOperations.search(query, ArticleSearchDocument.class);
        List<ArticleSearchDocument> documents = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        Map<Long, UserSummaryDto> authorMap = batchFetchUsers(documents.stream()
                .map(ArticleSearchDocument::getAuthorId)
                .collect(Collectors.toSet()));

        List<ArticleCardResp> list = documents.stream()
                .map(document -> {
                    UserSummaryDto author = authorMap.get(document.getAuthorId());
                    return ArticleCardResp.builder()
                            .articleId(document.getArticleId())
                            .title(document.getTitle())
                            .summary(document.getSummary())
                            .previewText(document.getSummary())
                            .coverUrl(document.getCoverUrl())
                            .coverColor(document.getCoverColor())
                            .readMinutes(document.getReadMinutes())
                            .durationCategory(document.getDurationCategory())
                            .authorId(document.getAuthorId())
                            .authorName(author == null ? null : author.getNickname())
                            .authorAvatar(author == null ? null : author.getAvatarUrl())
                            .publishedAt(document.getPublishedAt())
                            .updatedAt(document.getUpdatedAt())
                            .build();
                })
                .toList();

        long total = hits.getTotalHits();
        long pages = total == 0 ? 0 : (total + safePageSize - 1) / safePageSize;
        return SearchResp.builder()
                .keyword(keyword)
                .list(list)
                .total(total)
                .page(safePage)
                .pageSize(safePageSize)
                .pages(pages)
                .build();
    }

    /**
     * 构造 Elasticsearch 查询。
     * 采用标题和摘要双字段 should 匹配，并优先按相关度、再按发布时间倒序排序。
     */
    static NativeQuery buildSearchQuery(String keyword, int page, int pageSize) {
        return NativeQuery.builder()
                .withQuery(query -> query.bool(bool -> bool
                        .should(should -> should.match(match -> match
                                .field("title")
                                .query(keyword)))
                        .should(should -> should.match(match -> match
                                .field("summary")
                                .query(keyword)))
                        .minimumShouldMatch("1")))
                .withPageable(PageRequest.of(page - 1, pageSize))
                .withSort(sort -> sort.score(score -> score.order(SortOrder.Desc)))
                .withSort(sort -> sort.field(field -> field
                        .field("publishedAt")
                        .order(SortOrder.Desc)))
                .build();
    }

    /**
     * 批量补齐作者摘要信息。
     * 下游失败时不影响主搜索结果，只是作者信息为空。
     */
    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            return ResultUtils.requireOk(authInternalClient.batchUsers(new BatchUserQueryReq(userIds.stream().toList())))
                    .stream()
                    .collect(Collectors.toMap(UserSummaryDto::getId, user -> user));
        } catch (Exception ex) {
            log.warn("Failed to enrich search results with author summaries for {} users", userIds.size(), ex);
            return Collections.emptyMap();
        }
    }
}
