package com.platform.contract.review.client;

import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;
import com.platform.contract.review.dto.ReviewAssignmentDto;
import com.platform.kernel.util.Result;
import com.platform.web.support.feign.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "review-service", contextId = "reviewTaskClient", configuration = FeignCommonConfig.class)
public interface ReviewTaskClient {

    @GetMapping("/internal/review/tasks/{articleId}/assignment")
    Result<ReviewAssignmentDto> assignment(@PathVariable("articleId") Long articleId,
                                           @RequestParam("submissionId") String submissionId);

    @PostMapping("/internal/reviews/tasks/upsert")
    Result<Void> upsertTask(@RequestBody ReviewTaskUpsertReq req);

    @PostMapping("/internal/reviews/tasks/remove")
    Result<Void> removeTask(@RequestBody ReviewTaskRemoveReq req);
}
