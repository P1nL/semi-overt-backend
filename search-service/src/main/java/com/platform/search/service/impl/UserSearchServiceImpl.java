package com.platform.search.service.impl;

import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.api.ResultUtils;
import com.platform.search.api.resp.UserSearchResp;
import com.platform.search.service.UserSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSearchServiceImpl implements UserSearchService {

    private static final int MAX_LIMIT = 20;

    private final AuthUserQueryClient authInternalClient;

    @Override
    public UserSearchResp searchUsers(String keyword, int limit) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            return UserSearchResp.builder()
                    .keyword(normalized)
                    .list(Collections.emptyList())
                    .total(0L)
                    .build();
        }

        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<UserSummaryDto> users;
        try {
            users = ResultUtils.requireOk(authInternalClient.searchUsers(normalized, safeLimit));
        } catch (Exception e) {
            log.warn("Failed to search users from auth-service for keyword='{}': {}", normalized, e.getMessage());
            users = Collections.emptyList();
        }

        List<UserSearchResp.UserCardResp> list = users.stream()
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
                .total((long) list.size())
                .build();
    }
}
