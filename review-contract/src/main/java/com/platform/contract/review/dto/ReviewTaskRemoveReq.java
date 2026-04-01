package com.platform.contract.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核任务Remove相关类型，承载当前模块中的辅助职责。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTaskRemoveReq {
    private Long articleId;
    private String lastEventId;
}
