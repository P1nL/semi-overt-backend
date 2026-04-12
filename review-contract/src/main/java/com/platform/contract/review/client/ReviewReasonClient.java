package com.platform.contract.review.client;

import com.platform.contract.review.dto.BatchLatestReviewReasonReq;
import com.platform.contract.review.dto.LatestReviewReasonDto;
import com.platform.kernel.util.Result;
import com.platform.web.support.feign.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "review-service", configuration = FeignCommonConfig.class)
public interface ReviewReasonClient {

    @GetMapping("/internal/reviews/articles/{id}/latest")
    Result<LatestReviewReasonDto> latestReason(@PathVariable("id") Long articleId);

    @PostMapping("/internal/reviews/articles/batch-latest")
    Result<List<LatestReviewReasonDto>> batchLatestReasons(@RequestBody BatchLatestReviewReasonReq req);
}
