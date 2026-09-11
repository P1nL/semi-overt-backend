package com.platform.events.support;

import com.platform.events.enums.EventConsumeStatus;
import com.platform.kernel.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Runs inbox idempotency and the business side effect in one local REQUIRED transaction. */
@Service
public class EventConsumeService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiredTransaction;

    public EventConsumeService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.requiredTransaction = new TransactionTemplate(transactionManager);
        this.requiredTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public ConsumptionResult executeInTransaction(String eventId,
                                                   String consumer,
                                                   ThrowingRunnable handler) throws Exception {
        requireIdentity(eventId, consumer);
        try {
            ConsumptionResult result = requiredTransaction.execute(status -> {
                if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                    throw new IllegalStateException("inbox execution requires an active local transaction");
                }
                // ON DUPLICATE KEY UPDATE id=id atomically acquires the unique row's exclusive lock.
                // INSERT IGNORE followed by SELECT FOR UPDATE can leave competing sessions upgrading locks.
                jdbc.update("""
                        INSERT INTO event_consume_log(
                            event_id, consumer, status, consumed_at, error_message, created_at, updated_at
                        ) VALUES (?, ?, 'PROCESSING', NULL, NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                        ON DUPLICATE KEY UPDATE id = id
                        """, eventId, consumer);

                List<String> states = jdbc.query("""
                                SELECT status FROM event_consume_log
                                 WHERE event_id = ? AND consumer = ?
                                 FOR UPDATE
                                """,
                        (rs, rowNum) -> rs.getString(1), eventId, consumer);
                if (states.size() != 1) {
                    throw new IllegalStateException("inbox uniqueness invariant violated");
                }
                if (EventConsumeStatus.SUCCESS.name().equals(states.get(0))) {
                    return ConsumptionResult.ALREADY_PROCESSED;
                }

                jdbc.update("""
                        UPDATE event_consume_log
                           SET status = 'PROCESSING', consumed_at = NULL, error_message = NULL,
                               updated_at = CURRENT_TIMESTAMP(6)
                         WHERE event_id = ? AND consumer = ? AND status <> 'SUCCESS'
                        """, eventId, consumer);
                try {
                    handler.run();
                } catch (Exception ex) {
                    throw new HandlerExecutionException(ex);
                }
                int updated = jdbc.update("""
                        UPDATE event_consume_log
                           SET status = 'SUCCESS', consumed_at = CURRENT_TIMESTAMP(6), error_message = NULL,
                               updated_at = CURRENT_TIMESTAMP(6)
                         WHERE event_id = ? AND consumer = ? AND status = 'PROCESSING'
                        """, eventId, consumer);
                if (updated != 1) {
                    throw new IllegalStateException("inbox success transition lost");
                }
                return ConsumptionResult.HANDLED;
            });
            if (result == null) {
                throw new IllegalStateException("inbox transaction returned no result");
            }
            return result;
        } catch (HandlerExecutionException ex) {
            throw ex.original;
        }
    }

    public boolean isSuccess(String eventId, String consumer) {
        requireIdentity(eventId, consumer);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_consume_log
                 WHERE event_id = ? AND consumer = ? AND status = 'SUCCESS'
                """, Integer.class, eventId, consumer);
        return count != null && count == 1;
    }

    private static void requireIdentity(String eventId, String consumer) {
        if (eventId == null || eventId.isBlank() || eventId.length() > 64
                || consumer == null || consumer.isBlank() || consumer.length() > 128) {
            throw BusinessException.badRequest("eventId must contain 1..64 characters and consumer 1..128 characters");
        }
    }

    public enum ConsumptionResult {
        HANDLED,
        ALREADY_PROCESSED
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class HandlerExecutionException extends RuntimeException {
        private final Exception original;

        private HandlerExecutionException(Exception original) {
            super(original);
            this.original = original;
        }
    }
}