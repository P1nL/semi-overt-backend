package com.platform.review.service;

import com.platform.contract.review.dto.ReviewAssignmentDto;
import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.review.api.resp.ReviewReconciliationResp;

public interface ReviewTaskService {
    void upsertTask(ReviewTaskUpsertReq req);
    void removeTask(ReviewTaskRemoveReq req);
    void projectStatus(ArticleStatusChangedEvent event);
    ReviewAssignmentDto assignment(Long articleId, String submissionId);
    ReviewReconciliationResp.Item repairFromSnapshot(ArticleReviewSnapshotDto snapshot, String source);
}
