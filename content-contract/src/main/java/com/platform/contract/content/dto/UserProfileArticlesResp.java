package com.platform.contract.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户资料Articles相关类型，承载当前模块中的辅助职责。
 */

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
