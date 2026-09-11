package com.platform.contract.content.dto;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** Content-owned durable decision result, never an enqueue acknowledgment. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewDecisionResultDto {
    private String decisionId;
    private Long articleId;
    private String submissionId;
    private String state;
    private ArticleStatus status;
    private Long version;
    private LocalDateTime updatedAt;
    private Long adminId;
    private ReviewAction action;
    private String reason;
}
