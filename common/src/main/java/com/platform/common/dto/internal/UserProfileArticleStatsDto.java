package com.platform.common.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户资料文章StatsDto相关类型，承载当前模块中的辅助职责。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileArticleStatsDto {
    private long approved;
    private long pending;
    private long returned;
    private long rejected;
    private long draft;
    private Integer totalWordCount;
}
