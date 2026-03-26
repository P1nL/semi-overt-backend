package com.platform.service.impl;

import com.platform.client.AuthInternalClient;
import com.platform.common.api.ResultUtils;
import com.platform.common.dto.internal.BatchUserQueryReq;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.document.ArticleSearchDocument;
import com.platform.dto.resp.ArticleCardResp;
import com.platform.dto.resp.SearchResp;
import com.platform.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final AuthInternalClient authInternalClient;

    @Override
    public SearchResp search(String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));

        Criteria criteria = new Criteria("title").matches(keyword)
                .or(new Criteria("summary").matches(keyword));
        CriteriaQuery query = new CriteriaQuery(criteria, PageRequest.of(safePage - 1, safePageSize));

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

    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return ResultUtils.requireOk(authInternalClient.batchUsers(new BatchUserQueryReq(userIds.stream().toList())))
                .stream()
                .collect(Collectors.toMap(UserSummaryDto::getId, user -> user));
    }
}
