package com.platform.contract.review.client;

import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;
import com.platform.kernel.util.Result;
import com.platform.web.support.feign.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "review-service", configuration = FeignCommonConfig.class)
public interface ReviewTaskClient {

    @PostMapping("/internal/reviews/tasks/upsert")
    Result<Void> upsertTask(@RequestBody ReviewTaskUpsertReq req);

    @PostMapping("/internal/reviews/tasks/remove")
    Result<Void> removeTask(@RequestBody ReviewTaskRemoveReq req);
}
