package com.platform.review.service.impl;

import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.contract.review.dto.ReviewTaskUpsertReq;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.kernel.event.ArticleSubmittedEvent;
import com.platform.review.entity.ReviewLog;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.service.ReviewDecisionCoordinator;
import com.platform.review.service.ReviewEventService;
import com.platform.review.service.ReviewTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewEventServiceImpl implements ReviewEventService {

    private final ReviewTaskService reviewTaskService;
    private final ReviewDecisionCoordinator decisionCoordinator;
    private final ReviewLogMapper reviewLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void projectPendingTask(ArticleSubmittedEvent event) {
        reviewTaskService.upsertTask(ReviewTaskUpsertReq.builder()
                .articleId(event.getArticleId())
                .authorId(event.getAuthorId())
                .title(event.getTitle())
                .wordCount(event.getWordCount())
                .status(ArticleStatus.PENDING)
                .submitCount(event.getSubmitCount())
                .submittedAt(event.getSubmittedAt())
                .lastEventId(event.getEventId())
                .submissionId(event.getSubmissionId())
                .articleVersion(event.getArticleVersion())
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleArticleStatusChanged(ArticleStatusChangedEvent event) {
        reviewTaskService.projectStatus(event);
        if (event.getDecisionId() != null && !event.getDecisionId().isBlank()
                && event.getAction() != null && event.getAction() != ReviewAction.CANCEL) {
            decisionCoordinator.finalizeAuthoritativeResult(ReviewDecisionResultDto.builder()
                    .decisionId(event.getDecisionId())
                    .articleId(event.getArticleId())
                    .submissionId(event.getSubmissionId())
                    .state(ReviewDecisionCoordinatorImpl.FINAL)
                    .status(event.getToStatus())
                    .version(event.getArticleVersion())
                    .updatedAt(event.getUpdatedAt())
                    .adminId(event.getAdminId())
                    .action(event.getAction())
                    .reason(event.getReason())
                    .build());
            return;
        }
        if (event.getFromStatus() == ArticleStatus.PENDING
                && event.getToStatus() == ArticleStatus.DRAFT
                && event.getAction() == ReviewAction.CANCEL
                && event.getSubmissionId() != null
                && event.getArticleVersion() != null) {
            writeCancelLog(event);
        }
    }

    private void writeCancelLog(ArticleStatusChangedEvent event) {
        String decisionId = event.getDecisionId();
        if (decisionId == null || decisionId.isBlank()) {
            String key = "cancel|" + event.getArticleId() + "|" + event.getSubmissionId()
                    + "|" + event.getArticleVersion();
            decisionId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        }
        if (reviewLogMapper.selectByDecisionId(decisionId) != null) return;
        ReviewLog log = new ReviewLog();
        log.setArticleId(event.getArticleId());
        log.setOperatorId(event.getAuthorId());
        log.setAction(ReviewAction.CANCEL);
        log.setFromStatus(ArticleStatus.PENDING);
        log.setToStatus(ArticleStatus.DRAFT);
        log.setReason(event.getReason());
        log.setDecisionId(decisionId);
        log.setSubmissionId(event.getSubmissionId());
        log.setArticleVersion(event.getArticleVersion());
        reviewLogMapper.insert(log);
    }
}
