package com.platform.review.service;

import com.platform.kernel.api.PageResponse;
import com.platform.review.api.req.ReviewActionReq;
import com.platform.review.api.resp.ReviewActionResp;
import com.platform.review.api.resp.ReviewListItemResp;
import com.platform.review.api.resp.ReviewLogResp;

import java.util.List;


public interface ReviewService {

        PageResponse<ReviewListItemResp> getPendingList(Long currentAdminId, int page, int pageSize);

        ReviewActionResp doReview(Long articleId, Long currentAdminId, ReviewActionReq req);

        List<ReviewLogResp> getReviewLogs(Long articleId, Long currentUserId);
}



