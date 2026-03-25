package com.platform.common.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
