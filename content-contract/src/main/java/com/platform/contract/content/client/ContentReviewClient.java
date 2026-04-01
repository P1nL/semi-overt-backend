package com.platform.contract.content.client;

import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.kernel.util.Result;
import com.platform.web.support.feign.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "content-service", configuration = FeignCommonConfig.class)
public interface ContentReviewClient {

    @GetMapping("/internal/articles/{id}/review-snapshot")
    Result<ArticleReviewSnapshotDto> reviewSnapshot(@PathVariable("id") Long articleId);

    @PostMapping("/internal/articles/{id}/apply-review-result")
    Result<Void> applyReviewResult(@PathVariable("id") Long articleId,
                                   @RequestBody ApplyReviewResultReq req);
}
