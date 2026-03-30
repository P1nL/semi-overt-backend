package com.platform.common.dto.internal;

import com.platform.enums.ReviewAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Latest审核ReasonDto相关类型，承载当前模块中的辅助职责。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestReviewReasonDto {
    private Long articleId;
    private ReviewAction action;
    private String reason;
    private LocalDateTime createdAt;
}
