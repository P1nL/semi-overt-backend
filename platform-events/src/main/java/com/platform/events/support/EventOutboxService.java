package com.platform.events.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.events.entity.EventOutbox;
import com.platform.events.enums.EventOutboxStatus;
import com.platform.kernel.constant.EventConstants;
import com.platform.kernel.event.BaseDomainEvent;
import com.platform.kernel.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transactional outbox persistence and short, DB-clock fenced publisher claims. */
@Service
public class EventOutboxService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate independentTransaction;

    public EventOutboxService(JdbcTemplate jdbc,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Must join the producer's local business transaction. */
    @Transactional(rollbackFor = Exception.class)
    public void saveEvent(String aggregateType,
                          String aggregateId,
                          String eventType,
                          BaseDomainEvent event) {
        if (event == null || isBlank(event.getEventId())) {
            throw BusinessException.badRequest("eventId is required");
        }
        if (isBlank(aggregateType) || isBlank(aggregateId) || isBlank(eventType)) {
            throw BusinessException.badRequest("aggregateType, aggregateId and eventType are required");
        }
        jdbc.update("""
                        INSERT INTO event_outbox(
                            event_id, aggregate_type, aggregate_id, event_type, payload,
                            status, retry_count, next_retry_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP(6),
                                  CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                        """,
                event.getEventId(), aggregateType, aggregateId, eventType, writePayload(event));
    }

    /** Claims rows in a short independent transaction; no network I/O is inside this transaction. */
    public List<EventOutbox> claimPublishable(String aggregateType,
                                              List<String> eventTypes,
                                              int batchSize,
                                              String leaseOwner,
                                              Duration leaseDuration) {
        validateClaim(aggregateType, eventTypes, batchSize, leaseOwner, leaseDuration);
        List<EventOutbox> result = independentTransaction.execute(status -> claimInCurrentTransaction(
                aggregateType, eventTypes, batchSize, leaseOwner, leaseDuration));
        return result == null ? List.of() : result;
    }

    /** Convenience for the publisher's one-row-at-a-time lease discipline. */
    public EventOutbox claimNextPublishable(String aggregateType,
                                            List<String> eventTypes,
                                            String leaseOwner,
                                            Duration leaseDuration) {
        List<EventOutbox> claimed = claimPublishable(aggregateType, eventTypes, 1, leaseOwner, leaseDuration);
        return claimed.isEmpty() ? null : claimed.get(0);
    }

    private List<EventOutbox> claimInCurrentTransaction(String aggregateType,
                                                        List<String> eventTypes,
                                                        int batchSize,
                                                        String leaseOwner,
                                                        Duration leaseDuration) {
        String placeholders = String.join(",", eventTypes.stream().map(ignored -> "?").toList());
        String sql = """
                SELECT event_id, aggregate_type, aggregate_id, event_type, payload, status,
                       retry_count, next_retry_at, published_at, last_error,
                       lease_owner, lease_token, lease_until, created_at, updated_at
                  FROM event_outbox
                 WHERE aggregate_type = ?
                   AND event_type IN (%s)
                   AND status = 'PENDING'
                   AND next_retry_at <= CURRENT_TIMESTAMP(6)
                   AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(6))
                 ORDER BY created_at, event_id
                 LIMIT %d
                 FOR UPDATE SKIP LOCKED
                """.formatted(placeholders, batchSize);
        List<Object> args = new ArrayList<>();
        args.add(aggregateType);
        args.addAll(eventTypes);
        List<EventOutbox> rows = jdbc.query(sql, this::mapOutbox, args.toArray());
        List<EventOutbox> claimed = new ArrayList<>(rows.size());
        long leaseMicros = Math.max(1, leaseDuration.toNanos() / 1_000L);
        for (EventOutbox row : rows) {
            String token = UUID.randomUUID().toString().replace("-", "");
            int updated = jdbc.update("""
                            UPDATE event_outbox
                               SET lease_owner = ?, lease_token = ?,
                                   lease_until = TIMESTAMPADD(MICROSECOND, ?, CURRENT_TIMESTAMP(6)),
                                   updated_at = CURRENT_TIMESTAMP(6)
                             WHERE event_id = ?
                               AND status = 'PENDING'
                               AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP(6))
                            """,
                    leaseOwner, token, leaseMicros, row.getEventId());
            if (updated == 1) {
                row.setLeaseOwner(leaseOwner);
                row.setLeaseToken(token);
                row.setLeaseUntil(jdbc.queryForObject(
                        "SELECT lease_until FROM event_outbox WHERE event_id = ?",
                        LocalDateTime.class,
                        row.getEventId()));
                claimed.add(row);
            }
        }
        return claimed;
    }

    /** Confirm success is accepted only for the still-live owner/token lease according to the DB clock. */
    public boolean markPublished(EventOutbox claim) {
        requireClaim(claim);
        return Boolean.TRUE.equals(independentTransaction.execute(status -> jdbc.update("""
                        UPDATE event_outbox
                           SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(6), last_error = NULL,
                               lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                               updated_at = CURRENT_TIMESTAMP(6)
                         WHERE event_id = ? AND status = 'PENDING'
                           AND lease_owner = ? AND lease_token = ?
                           AND lease_until >= CURRENT_TIMESTAMP(6)
                        """,
                claim.getEventId(), claim.getLeaseOwner(), claim.getLeaseToken()) == 1));
    }

    /** Failure is persisted only by the still-live owner/token according to the DB clock. */
    public boolean markRetry(EventOutbox claim, String errorMessage) {
        requireClaim(claim);
        return Boolean.TRUE.equals(independentTransaction.execute(status -> {
            List<Integer> counts = jdbc.query("""
                            SELECT retry_count FROM event_outbox
                             WHERE event_id = ? AND status = 'PENDING'
                               AND lease_owner = ? AND lease_token = ?
                               AND lease_until >= CURRENT_TIMESTAMP(6)
                             FOR UPDATE
                            """,
                    (rs, rowNum) -> rs.getInt(1),
                    claim.getEventId(), claim.getLeaseOwner(), claim.getLeaseToken());
            if (counts.isEmpty()) {
                return false;
            }
            int retryCount = counts.get(0) + 1;
            boolean dead = retryCount >= 10;
            long delaySeconds = Math.min(300, retryCount * 15L);
            return jdbc.update("""
                            UPDATE event_outbox
                               SET retry_count = ?, last_error = ?, status = ?,
                                   next_retry_at = CASE WHEN ? THEN NULL
                                       ELSE TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6)) END,
                                   lease_owner = NULL, lease_token = NULL, lease_until = NULL,
                                   updated_at = CURRENT_TIMESTAMP(6)
                             WHERE event_id = ? AND status = 'PENDING'
                               AND lease_owner = ? AND lease_token = ?
                               AND lease_until >= CURRENT_TIMESTAMP(6)
                            """,
                    retryCount, truncate(errorMessage),
                    dead ? EventOutboxStatus.DEAD.name() : EventOutboxStatus.PENDING.name(),
                    dead, delaySeconds, claim.getEventId(), claim.getLeaseOwner(), claim.getLeaseToken()) == 1;
        }));
    }

    public EventConstants.EventRoute routeOf(String eventType) {
        return EventConstants.routeOf(eventType);
    }

    private EventOutbox mapOutbox(ResultSet rs, int rowNum) throws SQLException {
        EventOutbox row = new EventOutbox();
        row.setEventId(rs.getString("event_id"));
        row.setAggregateType(rs.getString("aggregate_type"));
        row.setAggregateId(rs.getString("aggregate_id"));
        row.setEventType(rs.getString("event_type"));
        row.setPayload(rs.getString("payload"));
        row.setStatus(rs.getString("status"));
        row.setRetryCount(rs.getInt("retry_count"));
        row.setNextRetryAt(toLocalDateTime(rs.getTimestamp("next_retry_at")));
        row.setPublishedAt(toLocalDateTime(rs.getTimestamp("published_at")));
        row.setLastError(rs.getString("last_error"));
        row.setLeaseOwner(rs.getString("lease_owner"));
        row.setLeaseToken(rs.getString("lease_token"));
        row.setLeaseUntil(toLocalDateTime(rs.getTimestamp("lease_until")));
        row.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        row.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return row;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void validateClaim(String aggregateType,
                               List<String> eventTypes,
                               int batchSize,
                               String leaseOwner,
                               Duration leaseDuration) {
        if (isBlank(aggregateType) || eventTypes == null || eventTypes.isEmpty()
                || eventTypes.stream().anyMatch(EventOutboxService::isBlank)) {
            throw new IllegalArgumentException("aggregateType and eventTypes are required");
        }
        if (batchSize <= 0 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        if (isBlank(leaseOwner) || leaseOwner.length() > 128) {
            throw new IllegalArgumentException("leaseOwner must contain 1..128 characters");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()
                || leaseDuration.toDays() > 1) {
            throw new IllegalArgumentException("leaseDuration must be positive and at most one day");
        }
    }

    private static void requireClaim(EventOutbox claim) {
        Objects.requireNonNull(claim, "claim");
        if (isBlank(claim.getEventId()) || isBlank(claim.getLeaseOwner()) || isBlank(claim.getLeaseToken())) {
            throw new IllegalArgumentException("a fenced outbox claim is required");
        }
    }

    private String writePayload(BaseDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw BusinessException.serverError("Failed to serialize event payload");
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}