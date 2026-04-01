package com.platform.review.service;

import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;


public interface ReviewTaskService {

    void upsertTask(ReviewTaskUpsertReq req);

    void removeTask(ReviewTaskRemoveReq req);
}



