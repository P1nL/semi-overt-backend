package com.platform.search.service.impl;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.exception.BusinessException;
import com.platform.search.api.resp.ArticleCardResp;
import com.platform.search.api.resp.SearchResp;
import com.platform.search.mapper.SearchArticleMapper;
import com.platform.search.model.SearchArticleRow;
import com.platform.search.model.SearchKeyword;
import com.platform.search.service.SearchIndexCapability;
import com.platform.search.service.SearchIndexStatus;
import com.platform.search.service.SearchService;
import com.platform.search.util.ArticlePreviewUtils;
import com.platform.search.util.SearchKeywordNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int CARD_PREVIEW_MAX_LENGTH = 120;

    private final SearchArticleMapper searchArticleMapper;
    private final AuthUserQueryClient authInternalClient;
    private final SearchIndexCapability searchIndexCapability;

    @Override
    public SearchResp search(String keyword, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        if (normalizedKeyword.isEmpty()) {
            return emptyResponse(normalizedKeyword, safePage, safePageSize);
        }

        SearchKeyword query = SearchKeywordNormalizer.article(normalizedKeyword);
        if (query.isBlank()) {
            return emptyResponse(normalizedKeyword, safePage, safePageSize);
        }

        SearchIndexStatus capability = searchIndexCapability.inspect();
        boolean fullText = capability.usable();
        long total;
        List<SearchArticleRow> rows;
        try {
            total = count(query, fullText);
            long offset = ((long) safePage - 1L) * safePageSize;
            rows = total == 0L
                    ? List.of()
                    : searchRows(query, fullText, offset, safePageSize);
        } catch (DataAccessException ex) {
            if (!fullText) {
                throw BusinessException.serverError("Article search is temporarily unavailable");
            }

            // FULLTEXT is optional. Retry the complete count + page query in
            // cleaned LIKE mode so total and list remain from one same semantic
            // path. A failure in that fallback is propagated as a server error.
            log.warn("FULLTEXT article search failed; retrying complete query with cleaned LIKE fallback: {}",
                    ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage());
            try {
                fullText = false;
                total = count(query, false);
                long offset = ((long) safePage - 1L) * safePageSize;
                rows = total == 0L
                        ? List.of()
                        : searchRows(query, false, offset, safePageSize);
            } catch (DataAccessException fallbackFailure) {
                throw BusinessException.serverError("Article search is temporarily unavailable");
            }
        }

        Map<Long, UserSummaryDto> authorMap = batchFetchUsers(rows.stream()
                .map(SearchArticleRow::getAuthorId)
                .collect(Collectors.toSet()));

        List<ArticleCardResp> list = rows.stream()
                .map(row -> toCard(row, authorMap.get(row.getAuthorId())))
                .toList();
        long pages = total == 0L ? 0L : (total + safePageSize - 1L) / safePageSize;

        return SearchResp.builder()
                .keyword(normalizedKeyword)
                .list(list)
                .total(total)
                .page(safePage)
                .pageSize(safePageSize)
                .pages(pages)
                .build();
    }

    private long count(SearchKeyword query, boolean fullText) {
        // Do not convert database failures into a successful empty page.
        return Math.max(0L, searchArticleMapper.countByKeyword(query, fullText));
    }

    private List<SearchArticleRow> searchRows(SearchKeyword query,
                                               boolean fullText,
                                               long offset,
                                               int limit) {
        return searchArticleMapper.searchByKeyword(query, fullText, offset, limit);
    }

    private ArticleCardResp toCard(SearchArticleRow row, UserSummaryDto author) {
        ArticleCardResp.ArticleAuthorResp authorResp = author == null ? null
                : ArticleCardResp.ArticleAuthorResp.builder()
                .id(author.getId())
                .username(author.getUsername())
                .nickname(author.getNickname())
                .avatarUrl(author.getAvatarUrl())
                .build();
        return ArticleCardResp.builder()
                .id(row.getArticleId())
                .articleId(row.getArticleId())
                .authorId(row.getAuthorId())
                .author(authorResp)
                .authorName(author == null ? null : author.getNickname())
                .authorAvatar(author == null ? null : author.getAvatarUrl())
                .title(row.getTitle())
                .content(null)
                .summary(row.getSummary())
                .previewText(ArticlePreviewUtils.extractPreviewText(row.getContent(), CARD_PREVIEW_MAX_LENGTH))
                .coverUrl(row.getCoverUrl())
                .coverColor(row.getCoverColor())
                .status(row.getStatus())
                .wordCount(row.getWordCount())
                .readMinutes(row.getReadMinutes())
                .durationCategory(row.getDurationCategory())
                .draftVisible(false)
                .publishedAt(row.getPublishedAt())
                .updatedAt(row.getUpdatedAt())
                .rejectReason(null)
                .build();
    }

    private SearchResp emptyResponse(String keyword, int page, int pageSize) {
        return SearchResp.builder()
                .keyword(keyword)
                .list(List.of())
                .total(0L)
                .page(page)
                .pageSize(pageSize)
                .pages(0L)
                .build();
    }

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