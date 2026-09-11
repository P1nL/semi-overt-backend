package com.platform.review.api.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewActionReq {
    @NotBlank(message = "审核动作不能为空")
    private String action;
    private String reason;
    private String decisionId;
    private String submissionId;
    private Long expectedVersion;
}
