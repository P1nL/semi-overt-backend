package com.platform.review.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.contract.review.dto.BatchLatestReviewReasonReq;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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
     * POST /articles/batch-latest — 批量查询多篇文章最近一次退回/拒绝原因。
     */
    @PostMapping("/articles/batch-latest")
    public Result<List<LatestReviewReasonDto>> batchLatest(@RequestBody BatchLatestReviewReasonReq req) {
        if (req == null || req.getArticleIds() == null || req.getArticleIds().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 一次查出所有相关 ReviewLog，按 createdAt 降序
        List<ReviewLog> logs = reviewLogMapper.selectList(new LambdaQueryWrapper<ReviewLog>()
                .in(ReviewLog::getArticleId, req.getArticleIds())
                .in(ReviewLog::getAction, ReviewAction.RETURN, ReviewAction.REJECT)
                .orderByDesc(ReviewLog::getCreatedAt));

        // 每篇文章只保留最新一条
        Map<Long, ReviewLog> latestPerArticle = new LinkedHashMap<>();
        for (ReviewLog log : logs) {
            latestPerArticle.putIfAbsent(log.getArticleId(), log);
        }

        List<LatestReviewReasonDto> result = latestPerArticle.values().stream()
                .map(log -> LatestReviewReasonDto.builder()
                        .articleId(log.getArticleId())
                        .action(log.getAction())
                        .reason(log.getReason())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
        return Result.ok(result);
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



