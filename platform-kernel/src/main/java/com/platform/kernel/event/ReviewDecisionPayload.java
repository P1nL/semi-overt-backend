package com.platform.kernel.event;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审核DecisionPayload相关类型，承载当前模块中的辅助职责。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecisionPayload {
    private Long articleId;
    private Long adminId;
    private ReviewAction action;
    private String reason;
    private LocalDateTime reviewedAt;
    private ArticleStatus fromStatus;
    private ArticleStatus toStatus;
    private String traceId;
}
