package com.platform.common.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileArticlesResp {
    private UserProfileArticleStatsDto stats;
    private List<UserProfileArticleItemDto> list;
    private long total;
    private int page;
    private int pageSize;
}
