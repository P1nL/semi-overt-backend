package com.platform.review.controller.internal;

import com.platform.contract.review.dto.ReviewAssignmentDto;
import com.platform.kernel.util.Result;
import com.platform.review.service.ReviewTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/review")
@RequiredArgsConstructor
public class InternalReviewAssignmentController {

    private final ReviewTaskService reviewTaskService;

    @GetMapping("/tasks/{articleId}/assignment")
    public Result<ReviewAssignmentDto> assignment(
            @PathVariable Long articleId,
            @RequestParam String submissionId) {
        return Result.ok(reviewTaskService.assignment(articleId, submissionId));
    }
}
