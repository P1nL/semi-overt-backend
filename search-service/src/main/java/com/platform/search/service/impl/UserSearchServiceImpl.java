package com.platform.search.service.impl;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.api.PageResponse;
import com.platform.kernel.api.ResultUtils;
import com.platform.search.api.resp.UserSearchResp;
import com.platform.search.service.UserSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSearchServiceImpl implements UserSearchService {

    private static final int MAX_PAGE_SIZE = 20;

    private final AuthUserQueryClient authInternalClient;

    @Override
    public UserSearchResp searchUsers(String keyword, int page, int pageSize) {
        String normalized = keyword == null ? "" : keyword.trim();
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));

        if (normalized.isEmpty()) {
            return UserSearchResp.builder()
                    .keyword(normalized)
                    .list(Collections.emptyList())
                    .total(0L)
                    .page((long) safePage)
                    .pageSize((long) safePageSize)
                    .pages(0L)
                    .build();
        }

        PageResponse<UserSummaryDto> userPage;
        try {
            userPage = ResultUtils.requireOk(authInternalClient.searchUsers(normalized, safePage, safePageSize));
        } catch (Exception e) {
            log.warn("Failed to search users from auth-service for keyword='{}': {}", normalized, e.getMessage());
            userPage = null;
        }

        List<UserSummaryDto> users = userPage == null ? Collections.emptyList() : userPage.getList();
        long total = userPage == null ? 0L : userPage.getTotal();
        long pages = userPage == null ? 0L : userPage.getPages();

        if (users == null || users.isEmpty()) {
            return UserSearchResp.builder()
                    .keyword(normalized)
                    .list(Collections.emptyList())
                    .total(total)
                    .page((long) safePage)
                    .pageSize((long) safePageSize)
                    .pages(pages)
                    .build();
        }

        List<UserSearchResp.UserCardResp> list = users.stream()
                .filter(Objects::nonNull)
                .filter(u -> u.getUsername() != null && !u.getUsername().isBlank())
                .map(u -> UserSearchResp.UserCardResp.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .nickname(u.getNickname())
                        .avatarUrl(u.getAvatarUrl())
                        .profilePath("/u/" + u.getUsername())
                        .build())
                .toList();

        return UserSearchResp.builder()
                .keyword(normalized)
                .list(list)
                .total(total)
                .page((long) safePage)
                .pageSize((long) safePageSize)
                .pages(pages)
                .build();
    }
}
