package com.platform.search.service.impl;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.api.ResultUtils;
import com.platform.search.api.resp.ArticleCardResp;
import com.platform.search.api.resp.SearchResp;
import com.platform.search.mapper.SearchArticleMapper;
import com.platform.search.model.SearchArticleRow;
import com.platform.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final SearchArticleMapper searchArticleMapper;
    private final AuthUserQueryClient authInternalClient;

        @Override
    public SearchResp search(String keyword, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        if (normalizedKeyword.isEmpty()) {
            return SearchResp.builder()
                    .keyword(normalizedKeyword)
                    .list(List.of())
                    .total(0L)
                    .page(safePage)
                    .pageSize(safePageSize)
                    .pages(0L)
                    .build();
        }

        String escapedKeyword = escapeLikePattern(normalizedKeyword);
        int offset = (safePage - 1) * safePageSize;
        long total = searchArticleMapper.countByKeyword(escapedKeyword);
        List<SearchArticleRow> rows = total == 0
                ? List.of()
                : searchArticleMapper.searchByKeyword(escapedKeyword, offset, safePageSize);

        Map<Long, UserSummaryDto> authorMap = batchFetchUsers(rows.stream()
                .map(SearchArticleRow::getAuthorId)
                .collect(Collectors.toSet()));

        List<ArticleCardResp> list = rows.stream()
                .map(row -> {
                    UserSummaryDto author = authorMap.get(row.getAuthorId());
                    return ArticleCardResp.builder()
                            .articleId(row.getArticleId())
                            .title(row.getTitle())
                            .summary(row.getSummary())
                            .previewText(row.getSummary())
                            .coverUrl(row.getCoverUrl())
                            .coverColor(row.getCoverColor())
                            .readMinutes(row.getReadMinutes())
                            .durationCategory(row.getDurationCategory())
                            .authorId(row.getAuthorId())
                            .authorName(author == null ? null : author.getNickname())
                            .authorAvatar(author == null ? null : author.getAvatarUrl())
                            .publishedAt(row.getPublishedAt())
                            .updatedAt(row.getUpdatedAt())
                            .build();
                })
                .toList();

        long pages = total == 0 ? 0 : (total + safePageSize - 1) / safePageSize;
        return SearchResp.builder()
                .keyword(normalizedKeyword)
                .list(list)
                .total(total)
                .page(safePage)
                .pageSize(safePageSize)
                .pages(pages)
                .build();
    }

    static String escapeLikePattern(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 閹靛綊鍣虹悰銉╃秷娴ｆ粏鈧懏鎲崇憰浣蜂繆閹垬鈧?     * 娑撳鐖舵径杈Е閺冩湹绗夎ぐ鍗炴惙娑撶粯鎮崇槐銏㈢波閺嬫粣绱濋崣顏呮Ц娴ｆ粏鈧懍淇婇幁顖欒礋缁屾亽鈧?     */
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


