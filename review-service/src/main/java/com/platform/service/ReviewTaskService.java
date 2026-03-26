package com.platform.service;

import com.platform.common.dto.internal.ReviewTaskRemoveReq;
import com.platform.common.dto.internal.ReviewTaskUpsertReq;

public interface ReviewTaskService {

    void upsertTask(ReviewTaskUpsertReq req);

    void removeTask(ReviewTaskRemoveReq req);
}
