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

/**
 * 审核任务投影服务实现。
 * review_tasks 是 review-service 针对待审核文章维护的投影视图，
 * 由提交事件和状态变更事件驱动增删改，供审核列表直接查询。
 */
@Service
@RequiredArgsConstructor
public class ReviewTaskServiceImpl implements ReviewTaskService {

    private final ReviewTaskMapper reviewTaskMapper;

    /**
     * 写入或更新待审核任务。
     * 仅当文章状态仍为 PENDING 时保留投影；否则直接删除对应任务，避免脏数据留在审核池中。
     */
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

    /**
     * 按文章维度删除审核任务投影。
     * 该操作天然幂等，不要求调用方保证任务一定存在。
     */
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
