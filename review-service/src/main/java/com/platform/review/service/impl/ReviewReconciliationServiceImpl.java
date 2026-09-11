package com.platform.review.service.impl;

import com.platform.contract.content.client.ContentReviewClient;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.kernel.util.Result;
import com.platform.review.api.resp.ReviewReconciliationResp;
import com.platform.review.entity.ReviewCommand;
import com.platform.review.entity.ReviewTask;
import com.platform.review.mapper.ReviewCommandMapper;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.ReviewDecisionCoordinator;
import com.platform.review.service.ReviewReconciliationService;
import com.platform.review.service.ReviewTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ReviewReconciliationServiceImpl implements ReviewReconciliationService {

    private final ContentReviewClient contentReviewClient;
    private final ReviewCommandMapper reviewCommandMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewDecisionCoordinator decisionCoordinator;
    private final ReviewTaskService reviewTaskService;
    private final AtomicLong pendingCursor = new AtomicLong();
    private final AtomicLong taskCursor = new AtomicLong();
    private final AtomicReference<String> processingCursor = new AtomicReference<>("");
    private final AtomicReference<ReviewReconciliationResp> lastProcessing = new AtomicReference<>(empty());
    private final AtomicReference<ReviewReconciliationResp> lastTasks = new AtomicReference<>(empty());

    public ReviewReconciliationServiceImpl(ContentReviewClient contentReviewClient,
                                           ReviewCommandMapper reviewCommandMapper,
                                           ReviewTaskMapper reviewTaskMapper,
                                           ReviewDecisionCoordinator decisionCoordinator,
                                           ReviewTaskService reviewTaskService) {
        this.contentReviewClient = contentReviewClient;
        this.reviewCommandMapper = reviewCommandMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.decisionCoordinator = decisionCoordinator;
        this.reviewTaskService = reviewTaskService;
    }

    @Override
    public ReviewReconciliationResp reconcileProcessing(int limit) {
        int bounded = boundedLimit(limit);
        List<ReviewReconciliationResp.Item> items = new ArrayList<>();
        int finalized = 0;
        int conflicts = 0;
        int unresolved = 0;
        String afterDecisionId = processingCursor.get();
        List<ReviewCommand> processing = reviewCommandMapper.selectProcessingAfter(afterDecisionId, bounded);
        for (ReviewCommand command : processing) {
            try {
                Result<ReviewDecisionResultDto> result = contentReviewClient.reviewDecisionResult(
                        command.getArticleId(), command.getDecisionId());
                if (result == null || result.getCode() == null || result.getCode() == 404
                        || result.getData() == null) {
                    unresolved++;
                    items.add(item(command, "UNRESOLVED", "Content result is not available yet"));
                    continue;
                }
                if (result.getCode() != 200 && result.getCode() != 409) {
                    unresolved++;
                    items.add(item(command, "UNRESOLVED", "Content result query code=" + result.getCode()));
                    continue;
                }
                ReviewCommand terminal = decisionCoordinator.finalizeAuthoritativeResult(result.getData());
                if (ReviewDecisionCoordinatorImpl.FINAL.equals(terminal.getState())) finalized++;
                else conflicts++;
                items.add(item(terminal, terminal.getState(), "Recovered from content durable result"));
            } catch (Exception ex) {
                unresolved++;
                items.add(item(command, "UNRESOLVED", safeMessage(ex)));
            }
        }
        if (processing.isEmpty() || processing.size() < bounded) {
            processingCursor.set("");
        } else {
            processingCursor.set(processing.get(processing.size() - 1).getDecisionId());
        }
        ReviewReconciliationResp report = ReviewReconciliationResp.builder()
                .scanned(items.size()).finalized(finalized).conflicts(conflicts)
                .unresolved(unresolved).items(List.copyOf(items)).build();
        lastProcessing.set(report);
        logSummary("processing", report);
        return report;
    }

    @Override
    public ReviewReconciliationResp reconcilePendingTasks(int limit) {
        int bounded = boundedLimit(limit);
        List<ReviewReconciliationResp.Item> items = new ArrayList<>();
        int repaired = 0;
        int unchanged = 0;
        int unresolved = 0;

        // Existing tombstones are checked individually against content authority.
        long afterTaskId = taskCursor.get();
        List<ReviewTask> tasks = reviewTaskMapper.selectForReconciliationAfter(afterTaskId, bounded);
        for (ReviewTask task : tasks) {
            try {
                Result<ArticleReviewSnapshotDto> result = contentReviewClient.reviewSnapshot(task.getArticleId());
                if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
                    unresolved++;
                    items.add(taskItem(task, "UNRESOLVED", "Snapshot unavailable"));
                    continue;
                }
                ReviewReconciliationResp.Item outcome = reviewTaskService.repairFromSnapshot(
                        result.getData(), "existing-task");
                items.add(outcome);
                if (isChanged(outcome.getOutcome())) repaired++; else unchanged++;
            } catch (Exception ex) {
                unresolved++;
                items.add(taskItem(task, "UNRESOLVED", safeMessage(ex)));
            }
        }

        if (tasks.isEmpty() || tasks.size() < bounded) {
            taskCursor.set(0L);
        } else {
            taskCursor.set(tasks.get(tasks.size() - 1).getId());
        }

        // Content-owned bounded keyset page finds PENDING articles which have no review row at all.
        long afterId = pendingCursor.get();
        try {
            Result<List<ArticleReviewSnapshotDto>> result = contentReviewClient.pendingReviewSnapshots(afterId, bounded);
            if (result != null && result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
                List<ArticleReviewSnapshotDto> snapshots = result.getData();
                long next = afterId;
                for (ArticleReviewSnapshotDto snapshot : snapshots) {
                    next = Math.max(next, snapshot.getArticleId() == null ? next : snapshot.getArticleId());
                    try {
                        ReviewReconciliationResp.Item outcome = reviewTaskService.repairFromSnapshot(
                                snapshot, "pending-scan-after-" + afterId);
                        items.add(outcome);
                        if (isChanged(outcome.getOutcome())) repaired++; else unchanged++;
                    } catch (Exception ex) {
                        unresolved++;
                        items.add(ReviewReconciliationResp.Item.builder()
                                .articleId(snapshot.getArticleId())
                                .submissionId(snapshot.getSubmissionId())
                                .articleVersion(snapshot.getVersion())
                                .outcome("UNRESOLVED").message(safeMessage(ex)).build());
                    }
                }
                pendingCursor.set(snapshots.size() < bounded ? 0L : next);
            } else {
                unresolved++;
                items.add(ReviewReconciliationResp.Item.builder()
                        .outcome("UNRESOLVED").message("Pending snapshot page unavailable").build());
            }
        } catch (Exception ex) {
            unresolved++;
            items.add(ReviewReconciliationResp.Item.builder()
                    .outcome("UNRESOLVED").message(safeMessage(ex)).build());
        }

        ReviewReconciliationResp report = ReviewReconciliationResp.builder()
                .scanned(items.size()).repaired(repaired).unchanged(unchanged)
                .unresolved(unresolved).items(List.copyOf(items)).build();
        lastTasks.set(report);
        logSummary("tasks", report);
        return report;
    }

    @Override
    public ReviewReconciliationResp repairArticle(Long articleId) {
        List<ReviewReconciliationResp.Item> items = new ArrayList<>();
        try {
            Result<ArticleReviewSnapshotDto> result = contentReviewClient.reviewSnapshot(articleId);
            if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
                items.add(ReviewReconciliationResp.Item.builder().articleId(articleId)
                        .outcome("UNRESOLVED").message("Snapshot unavailable").build());
                return ReviewReconciliationResp.builder().scanned(1).unresolved(1).items(items).build();
            }
            ReviewReconciliationResp.Item item = reviewTaskService.repairFromSnapshot(
                    result.getData(), "manual-repair");
            items.add(item);
            int repaired = isChanged(item.getOutcome()) ? 1 : 0;
            ReviewReconciliationResp report = ReviewReconciliationResp.builder().scanned(1).repaired(repaired)
                    .unchanged(repaired == 0 ? 1 : 0).items(List.copyOf(items)).build();
            lastTasks.set(report);
            logSummary("manual-article", report);
            return report;
        } catch (Exception ex) {
            items.add(ReviewReconciliationResp.Item.builder().articleId(articleId)
                    .outcome("UNRESOLVED").message(safeMessage(ex)).build());
            ReviewReconciliationResp report = ReviewReconciliationResp.builder()
                    .scanned(1).unresolved(1).items(List.copyOf(items)).build();
            lastTasks.set(report);
            logSummary("manual-article", report);
            return report;
        }
    }

    @Override
    public ReviewReconciliationResp lastProcessingReport() {
        return lastProcessing.get();
    }

    @Override
    public ReviewReconciliationResp lastTaskReport() {
        return lastTasks.get();
    }

    private ReviewReconciliationResp.Item item(ReviewCommand command, String outcome, String message) {
        return ReviewReconciliationResp.Item.builder()
                .articleId(command.getArticleId()).decisionId(command.getDecisionId())
                .submissionId(command.getSubmissionId()).articleVersion(command.getArticleVersion())
                .outcome(outcome).message(message).build();
    }

    private ReviewReconciliationResp.Item taskItem(ReviewTask task, String outcome, String message) {
        return ReviewReconciliationResp.Item.builder()
                .articleId(task.getArticleId()).decisionId(task.getDecisionId())
                .submissionId(task.getSubmissionId()).articleVersion(task.getLastAppliedVersion())
                .outcome(outcome).message(message).build();
    }

    private int boundedLimit(int limit) {
        return Math.max(1, Math.min(100, limit));
    }

    private boolean isChanged(String outcome) {
        return !"UNCHANGED".equals(outcome) && !"STALE_IGNORED".equals(outcome)
                && !"VERSION_CONFLICT_IGNORED".equals(outcome);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName()
                : message.substring(0, Math.min(300, message.length()));
    }

    private void logSummary(String kind, ReviewReconciliationResp report) {
        log.info("Review reconciliation completed: kind={}, scanned={}, repaired={}, finalized={}, conflicts={}, unchanged={}, unresolved={}",
                kind, report.getScanned(), report.getRepaired(), report.getFinalized(), report.getConflicts(),
                report.getUnchanged(), report.getUnresolved());
    }

    private static ReviewReconciliationResp empty() {
        return ReviewReconciliationResp.builder().items(List.of()).build();
    }
}
