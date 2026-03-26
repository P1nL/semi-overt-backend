package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.common.dto.internal.ReviewTaskRemoveReq;
import com.platform.common.dto.internal.ReviewTaskUpsertReq;
import com.platform.entity.ReviewTask;
import com.platform.enums.ArticleStatus;
import com.platform.exception.BusinessException;
import com.platform.mapper.ReviewTaskMapper;
import com.platform.service.ReviewTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewTaskServiceImpl implements ReviewTaskService {

    private final ReviewTaskMapper reviewTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsertTask(ReviewTaskUpsertReq req) {
        if (req == null || req.getArticleId() == null) {
            throw BusinessException.badRequest("articleId is required");
        }
        if (req.getStatus() != ArticleStatus.PENDING) {
            reviewTaskMapper.delete(new LambdaQueryWrapper<ReviewTask>()
                    .eq(ReviewTask::getArticleId, req.getArticleId()));
            return;
        }

        ReviewTask task = reviewTaskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getArticleId, req.getArticleId())
                .last("LIMIT 1"));

        if (task == null) {
            task = new ReviewTask();
            task.setArticleId(req.getArticleId());
        }

        task.setAuthorId(req.getAuthorId());
        task.setTitle(req.getTitle());
        task.setWordCount(req.getWordCount());
        task.setStatus(req.getStatus());
        task.setSubmitCount(req.getSubmitCount());
        task.setSubmittedAt(req.getSubmittedAt());
        task.setLastEventId(req.getLastEventId());

        if (task.getId() == null) {
            reviewTaskMapper.insert(task);
        } else {
            reviewTaskMapper.updateById(task);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTask(ReviewTaskRemoveReq req) {
        if (req == null || req.getArticleId() == null) {
            throw BusinessException.badRequest("articleId is required");
        }
        reviewTaskMapper.delete(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getArticleId, req.getArticleId()));
    }
}
