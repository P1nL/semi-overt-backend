package com.platform.review.scheduling;

import com.platform.review.service.ReviewReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewReconciliationScheduler {
    private final ReviewReconciliationService reconciliationService;
    private final AtomicBoolean running = new AtomicBoolean();

    @Value("${platform.review.reconciliation-batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${platform.review.reconciliation-delay-ms:15000}")
    public void reconcile() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Skip overlapping review reconciliation run");
            return;
        }
        try {
            reconciliationService.reconcileProcessing(batchSize);
            reconciliationService.reconcilePendingTasks(batchSize);
        } finally {
            running.set(false);
        }
    }
}
