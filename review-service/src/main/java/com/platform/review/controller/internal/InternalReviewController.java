package com.platform.review.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.contract.review.dto.LatestReviewReasonDto;
import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;
import com.platform.review.entity.ReviewLog;
import com.platform.kernel.enums.ReviewAction;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.service.ReviewTaskService;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/internal/reviews")
@RequiredArgsConstructor
public class InternalReviewController {

    private final ReviewLogMapper reviewLogMapper;
    private final ReviewTaskService reviewTaskService;

    /**
     * 婢跺嫮鎮?GET /articles/{id}/latest 鐠囬攱鐪伴妴?     */
    @GetMapping("/articles/{id}/latest")
    public Result<LatestReviewReasonDto> latest(@PathVariable Long id) {
        ReviewLog log = reviewLogMapper.selectOne(new LambdaQueryWrapper<ReviewLog>()
                .eq(ReviewLog::getArticleId, id)
                .in(ReviewLog::getAction, ReviewAction.RETURN, ReviewAction.REJECT)
                .orderByDesc(ReviewLog::getCreatedAt)
                .last("LIMIT 1"));

        if (log == null) {
            return Result.ok(null);
        }

        return Result.ok(LatestReviewReasonDto.builder()
                .articleId(id)
                .action(log.getAction())
                .reason(log.getReason())
                .createdAt(log.getCreatedAt())
                .build());
    }

    /**
     * 婢跺嫮鎮?POST /tasks/upsert 鐠囬攱鐪伴妴?     */
    @PostMapping("/tasks/upsert")
    public Result<Void> upsertTask(@RequestBody ReviewTaskUpsertReq req) {
        reviewTaskService.upsertTask(req);
        return Result.ok();
    }

    /**
     * 婢跺嫮鎮?POST /tasks/remove 鐠囬攱鐪伴妴?     */
    @PostMapping("/tasks/remove")
    public Result<Void> removeTask(@RequestBody ReviewTaskRemoveReq req) {
        reviewTaskService.removeTask(req);
        return Result.ok();
    }
}



