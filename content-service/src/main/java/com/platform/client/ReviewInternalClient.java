package com.platform.client;

import com.platform.common.dto.internal.LatestReviewReasonDto;
import com.platform.common.feign.FeignCommonConfig;
import com.platform.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "review-service", configuration = FeignCommonConfig.class)
public interface ReviewInternalClient {

    @GetMapping("/internal/reviews/articles/{id}/latest")
    Result<LatestReviewReasonDto> latestReason(@PathVariable("id") Long articleId);
}
