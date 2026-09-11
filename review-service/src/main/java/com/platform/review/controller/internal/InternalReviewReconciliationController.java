package com.platform.review.controller.internal;

import com.platform.kernel.util.Result;
import com.platform.review.api.resp.ReviewReconciliationResp;
import com.platform.review.service.ReviewReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/reviews/reconciliation")
@RequiredArgsConstructor
public class InternalReviewReconciliationController {

    private final ReviewReconciliationService reconciliationService;

    @GetMapping("/reports")
    public Result<Map<String, ReviewReconciliationResp>> reports() {
        return Result.ok(Map.of(
                "processing", reconciliationService.lastProcessingReport(),
                "tasks", reconciliationService.lastTaskReport()));
    }

    @PostMapping("/processing")
    public Result<ReviewReconciliationResp> processing(
            @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(reconciliationService.reconcileProcessing(limit));
    }

    @PostMapping("/tasks")
    public Result<ReviewReconciliationResp> tasks(
            @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(reconciliationService.reconcilePendingTasks(limit));
    }

    @PostMapping("/articles/{articleId}")
    public Result<ReviewReconciliationResp> article(@PathVariable Long articleId) {
        return Result.ok(reconciliationService.repairArticle(articleId));
    }
}
