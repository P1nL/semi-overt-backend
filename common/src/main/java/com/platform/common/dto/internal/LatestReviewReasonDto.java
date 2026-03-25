package com.platform.common.dto.internal;

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
public class LatestReviewReasonDto {
    private Long articleId;
    private ReviewAction action;
    private String reason;
    private LocalDateTime createdAt;
}
