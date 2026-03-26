package com.platform.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.common.dto.internal.LatestReviewReasonDto;
import com.platform.common.dto.internal.ReviewTaskRemoveReq;
import com.platform.common.dto.internal.ReviewTaskUpsertReq;
import com.platform.entity.ReviewLog;
import com.platform.enums.ReviewAction;
import com.platform.mapper.ReviewLogMapper;
import com.platform.service.ReviewTaskService;
import com.platform.util.Result;
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

    @PostMapping("/tasks/upsert")
    public Result<Void> upsertTask(@RequestBody ReviewTaskUpsertReq req) {
        reviewTaskService.upsertTask(req);
        return Result.ok();
    }

    @PostMapping("/tasks/remove")
    public Result<Void> removeTask(@RequestBody ReviewTaskRemoveReq req) {
        reviewTaskService.removeTask(req);
        return Result.ok();
    }
}
