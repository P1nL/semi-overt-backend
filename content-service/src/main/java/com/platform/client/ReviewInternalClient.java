package com.platform.client;

import com.platform.common.dto.internal.LatestReviewReasonDto;
import com.platform.common.dto.internal.ReviewTaskRemoveReq;
import com.platform.common.dto.internal.ReviewTaskUpsertReq;
import com.platform.common.feign.FeignCommonConfig;
import com.platform.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 审核内部远程客户端，负责发起跨服务调用。
 */

@FeignClient(name = "review-service", configuration = FeignCommonConfig.class)
public interface ReviewInternalClient {

    @GetMapping("/internal/reviews/articles/{id}/latest")
    Result<LatestReviewReasonDto> latestReason(@PathVariable("id") Long articleId);

    @PostMapping("/internal/reviews/tasks/upsert")
    Result<Void> upsertTask(@RequestBody ReviewTaskUpsertReq req);

    @PostMapping("/internal/reviews/tasks/remove")
    Result<Void> removeTask(@RequestBody ReviewTaskRemoveReq req);
}
