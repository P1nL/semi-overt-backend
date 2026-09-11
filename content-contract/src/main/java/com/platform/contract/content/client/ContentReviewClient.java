package com.platform.contract.content.client;

import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.kernel.util.Result;
import com.platform.web.support.feign.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@FeignClient(name = "content-service", configuration = FeignCommonConfig.class)
public interface ContentReviewClient {

    /** Bounded keyset page for review-owned projection reconciliation. */
    @GetMapping("/internal/articles/review-pending")
    Result<List<ArticleReviewSnapshotDto>> pendingReviewSnapshots(@RequestParam("afterId") long afterId,
                                                                 @RequestParam("limit") int limit);

    @GetMapping("/internal/articles/{id}/review-snapshot")
    Result<ArticleReviewSnapshotDto> reviewSnapshot(@PathVariable("id") Long articleId);

    @PostMapping("/internal/articles/{id}/apply-review-result")
    Result<ReviewDecisionResultDto> applyReviewResult(@PathVariable("id") Long articleId,
                                   @RequestBody ApplyReviewResultReq req);

    @GetMapping("/internal/articles/{id}/review-decisions/{decisionId}")
    Result<ReviewDecisionResultDto> reviewDecisionResult(@PathVariable("id") Long articleId,
                                                       @PathVariable("decisionId") String decisionId);
}
