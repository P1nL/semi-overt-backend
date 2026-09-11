package com.platform.search.service.impl;

import com.platform.kernel.exception.BusinessException;
import com.platform.search.api.resp.UserSearchResp;
import com.platform.search.mapper.SearchUserMapper;
import com.platform.search.model.SearchKeyword;
import com.platform.search.model.SearchUserRow;
import com.platform.search.service.UserSearchService;
import com.platform.search.util.SearchKeywordNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSearchServiceImpl implements UserSearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final SearchUserMapper searchUserMapper;

    @Override
    public UserSearchResp searchUsers(String keyword, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        SearchKeyword query = SearchKeywordNormalizer.user(normalizedKeyword);
        if (query.isBlank()) {
            return emptyResponse(normalizedKeyword, safePage, safePageSize);
        }

        long total;
        List<SearchUserRow> rows;
        try {
            total = Math.max(0L, searchUserMapper.countByKeyword(query));
            long offset = ((long) safePage - 1L) * safePageSize;
            rows = total == 0L
                    ? Collections.emptyList()
                    : searchUserMapper.searchByKeyword(query, offset, safePageSize);
        } catch (DataAccessException ex) {
            throw BusinessException.serverError("User search is temporarily unavailable");
        }
        long pages = total == 0L ? 0L : (total + safePageSize - 1L) / safePageSize;

        List<UserSearchResp.UserCardResp> list = rows == null ? List.of() : rows.stream()
                .filter(row -> row != null && row.getUsername() != null && !row.getUsername().isBlank())
                .map(row -> UserSearchResp.UserCardResp.builder()
                        .id(row.getId())
                        .username(row.getUsername())
                        .nickname(row.getNickname())
                        .avatarUrl(row.getAvatarUrl())
                        .profilePath("/u/" + row.getUsername())
                        .build())
                .toList();

        return UserSearchResp.builder()
                .keyword(normalizedKeyword)
                .list(list)
                .total(total)
                .page((long) safePage)
                .pageSize((long) safePageSize)
                .pages(pages)
                .build();
    }

    private UserSearchResp emptyResponse(String keyword, int page, int pageSize) {
        return UserSearchResp.builder()
                .keyword(keyword)
                .list(List.of())
                .total(0L)
                .page((long) page)
                .pageSize((long) pageSize)
                .pages(0L)
                .build();
    }
}