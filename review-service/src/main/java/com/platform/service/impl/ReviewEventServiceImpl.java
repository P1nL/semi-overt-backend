package com.platform.service.impl;

import com.platform.client.ContentInternalClient;
import com.platform.common.api.ResultUtils;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.dto.internal.ReviewTaskRemoveReq;
import com.platform.common.dto.internal.ReviewTaskUpsertReq;
import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.common.event.ArticleSubmittedEvent;
import com.platform.entity.ReviewLog;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import com.platform.mapper.ReviewLogMapper;
import com.platform.service.ReviewEventService;
import com.platform.service.ReviewTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审核域事件处理服务实现。
 * 负责把 content-service 发出的文章事件投影到 review-service 的任务池和审核日志中。
 */
@Service
@RequiredArgsConstructor
public class ReviewEventServiceImpl implements ReviewEventService {

    private final ContentInternalClient contentInternalClient;
    private final ReviewTaskService reviewTaskService;
    private final ReviewLogMapper reviewLogMapper;

    /**
     * 处理文章提交事件。
     * 通过再次拉取文章快照确认最新状态，避免消费到过期事件时把错误数据投影到审核池。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void projectPendingTask(ArticleSubmittedEvent event) {
        ArticleReviewSnapshotDto snapshot = ResultUtils.requireOk(contentInternalClient.reviewSnapshot(event.getArticleId()));
        if (snapshot == null) {
            return;
        }
        if (snapshot.getStatus() != ArticleStatus.PENDING) {
            reviewTaskService.removeTask(ReviewTaskRemoveReq.builder()
                    .articleId(event.getArticleId())
                    .lastEventId(event.getEventId())
                    .build());
            return;
        }
        reviewTaskService.upsertTask(ReviewTaskUpsertReq.builder()
                .articleId(snapshot.getArticleId())
                .authorId(snapshot.getAuthorId())
                .title(snapshot.getTitle())
                .wordCount(snapshot.getWordCount())
                .status(snapshot.getStatus())
                .submitCount(snapshot.getSubmitCount())
                .submittedAt(snapshot.getLastSubmittedAt())
                .lastEventId(event.getEventId())
                .build());
    }

    /**
     * 处理文章状态变更事件。
     * 只要文章离开 PENDING，就从审核任务池移除；若是作者主动撤回，还会补写一条 CANCEL 审核日志。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleArticleStatusChanged(ArticleStatusChangedEvent event) {
        if (event.getToStatus() != ArticleStatus.PENDING) {
            reviewTaskService.removeTask(ReviewTaskRemoveReq.builder()
                    .articleId(event.getArticleId())
                    .lastEventId(event.getEventId())
                    .build());
        }

        if (event.getFromStatus() == ArticleStatus.PENDING && event.getToStatus() == ArticleStatus.DRAFT) {
            ReviewLog reviewLog = new ReviewLog();
            reviewLog.setArticleId(event.getArticleId());
            reviewLog.setOperatorId(event.getAuthorId());
            reviewLog.setAction(ReviewAction.CANCEL);
            reviewLog.setFromStatus(ArticleStatus.PENDING);
            reviewLog.setToStatus(ArticleStatus.DRAFT);
            reviewLog.setReason(null);
            reviewLogMapper.insert(reviewLog);
        }
    }
}
