package com.platform.service;

import com.platform.common.dto.internal.ReviewTaskRemoveReq;
import com.platform.common.dto.internal.ReviewTaskUpsertReq;

/**
 * 审核任务业务接口，定义对外暴露的服务能力。
 */

public interface ReviewTaskService {

    void upsertTask(ReviewTaskUpsertReq req);

    void removeTask(ReviewTaskRemoveReq req);
}
