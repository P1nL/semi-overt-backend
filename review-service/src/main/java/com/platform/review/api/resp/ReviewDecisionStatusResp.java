package com.platform.review.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewDecisionStatusResp {
    private String decisionId;
    private String state;
    private ArticleStatus status;
    private LocalDateTime updatedAt;
}
