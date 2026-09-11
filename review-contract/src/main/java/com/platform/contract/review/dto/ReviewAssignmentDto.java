package com.platform.contract.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewAssignmentDto {
    private Long articleId;
    private String submissionId;
    private Long assignedAdminId;
}
