package com.platform.common.dto.internal;

import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
