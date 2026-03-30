package com.platform.common.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户资料ArticlesQuery相关类型，承载当前模块中的辅助职责。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileArticlesQueryReq {
    private Long authorId;
    private String tab;
    private int page;
    private int pageSize;
}
