package com.platform.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;
import com.platform.review.entity.ReviewTask;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.exception.BusinessException;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.ReviewTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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

        ReviewTask task = new ReviewTask();
        task.setArticleId(req.getArticleId());
        task.setAuthorId(req.getAuthorId());
        task.setTitle(req.getTitle());
        task.setWordCount(req.getWordCount());
        task.setStatus(ArticleStatus.PENDING);
        task.setSubmitCount(req.getSubmitCount());
        task.setSubmittedAt(req.getSubmittedAt());
        task.setLastEventId(req.getLastEventId());

        // 依赖 review_tasks 表在 article_id 上存在 UNIQUE INDEX，避免并发 upsert 产生重复任务
        boolean exists = reviewTaskMapper.exists(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getArticleId, req.getArticleId()));
        if (exists) {
            reviewTaskMapper.update(task, new LambdaUpdateWrapper<ReviewTask>()
                    .eq(ReviewTask::getArticleId, req.getArticleId()));
        } else {
            try {
                reviewTaskMapper.insert(task);
            } catch (DuplicateKeyException e) {
                reviewTaskMapper.update(task, new LambdaUpdateWrapper<ReviewTask>()
                        .eq(ReviewTask::getArticleId, req.getArticleId()));
            }
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



