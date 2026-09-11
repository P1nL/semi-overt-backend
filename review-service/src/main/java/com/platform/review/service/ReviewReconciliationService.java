package com.platform.review.service;

import com.platform.review.api.resp.ReviewReconciliationResp;

public interface ReviewReconciliationService {
    ReviewReconciliationResp reconcileProcessing(int limit);
    ReviewReconciliationResp reconcilePendingTasks(int limit);
    ReviewReconciliationResp repairArticle(Long articleId);
    ReviewReconciliationResp lastProcessingReport();
    ReviewReconciliationResp lastTaskReport();
}
