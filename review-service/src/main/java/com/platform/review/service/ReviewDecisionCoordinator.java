package com.platform.review.service;

import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.review.entity.ReviewCommand;

public interface ReviewDecisionCoordinator {
    ReviewCommand claim(Long articleId, Long adminId, String requestedDecisionId,
                        String action, String reason, String submissionId, Long expectedVersion);
    ReviewCommand finalizeAuthoritativeResult(ReviewDecisionResultDto result);
    ReviewCommand find(String decisionId);
}
