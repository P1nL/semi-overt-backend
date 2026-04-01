package com.platform.review.service.impl;

import com.platform.contract.content.client.ContentReviewClient;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.review.dto.ReviewTaskRemoveReq;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.platform.review.entity.ReviewLog;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.service.ReviewEventService;
import com.platform.review.service.ReviewTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewEventServiceImpl implements ReviewEventService {

    private final ContentReviewClient contentInternalClient;
    private final ReviewTaskService reviewTaskService;
    private final ReviewLogMapper reviewLogMapper;

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




