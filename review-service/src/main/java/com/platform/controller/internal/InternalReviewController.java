package com.platform.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.common.dto.internal.LatestReviewReasonDto;
import com.platform.entity.ReviewLog;
import com.platform.enums.ReviewAction;
import com.platform.mapper.ReviewLogMapper;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/reviews/articles")
@RequiredArgsConstructor
public class InternalReviewController {

    private final ReviewLogMapper reviewLogMapper;

    @GetMapping("/{id}/latest")
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
}
