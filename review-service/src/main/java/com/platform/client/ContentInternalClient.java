package com.platform.client;

import com.platform.common.dto.internal.ApplyReviewResultReq;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.feign.FeignCommonConfig;
import com.platform.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "content-service", configuration = FeignCommonConfig.class)
public interface ContentInternalClient {

    @GetMapping("/internal/articles/{id}/review-snapshot")
    Result<ArticleReviewSnapshotDto> reviewSnapshot(@PathVariable("id") Long articleId);

    @PostMapping("/internal/articles/{id}/apply-review-result")
    Result<Void> applyReviewResult(@PathVariable("id") Long articleId,
                                   @RequestBody ApplyReviewResultReq req);
}
