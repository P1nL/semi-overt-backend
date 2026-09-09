package com.platform.auth.session;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Database-atomic request budgets, independent of the caller's business transaction. */
@Component
public class JdbcRequestBudget {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public JdbcRequestBudget(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this(jdbc, manager, Clock.systemUTC());
    }
    public JdbcRequestBudget(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setTimeout(5);
    }
    public record Limit(String key, int maxRequests, long windowSeconds) {
        public Limit {
            if (key == null || key.isBlank() || key.length() > 160 || maxRequests < 1
                    || windowSeconds < 1 || windowSeconds > 90L * 86400) {
                throw new IllegalArgumentException("Invalid rate limit specification");
            }
        }
    }
    public record Decision(boolean allowed, long retryAfterSeconds) {}
    private record Bucket(Limit limit, long windowStart, long count) {}

    public Decision tryAcquire(List<Limit> limits) {
        if (limits == null || limits.isEmpty() || limits.size() > 16) {
            throw new IllegalArgumentException("One to sixteen budgets are required");
        }
        var ordered = limits.stream().sorted(Comparator.comparing(Limit::key)).toList();
        var keys = new HashSet<String>();
        for (Limit limit : ordered) {
            if (!keys.add(limit.key())) throw new IllegalArgumentException("Duplicate budget key");
        }
        long now = clock.millis();
        return Objects.requireNonNull(transactions.execute(status -> {
            var buckets = new ArrayList<Bucket>();
            long retryAfter = 0;
            for (Limit limit : ordered) {
                long windowMillis = limit.windowSeconds() * 1000;
                // No-op upsert also serializes first use of a previously absent key.
                jdbc.update("""
                        INSERT INTO rate_limit_buckets (bucket_key, window_started_at, request_count, expires_at)
                        VALUES (?, ?, 0, ?)
                        ON DUPLICATE KEY UPDATE bucket_key = VALUES(bucket_key)
                        """, limit.key(), now, now + windowMillis * 2);
                Bucket bucket = jdbc.queryForObject("""
                        SELECT window_started_at, request_count FROM rate_limit_buckets
                        WHERE bucket_key = ? FOR UPDATE
                        """, (rs, row) -> new Bucket(limit, rs.getLong(1), rs.getLong(2)), limit.key());
                if (bucket == null) throw new IllegalStateException("Rate budget disappeared while locked");
                if (now - bucket.windowStart() >= windowMillis) bucket = new Bucket(limit, now, 0);
                buckets.add(bucket);
                if (bucket.count() >= limit.maxRequests()) {
                    retryAfter = Math.max(retryAfter,
                            Math.max(1, (bucket.windowStart() + windowMillis - now + 999) / 1000));
                }
            }
            if (retryAfter > 0) {
                // Roll back inserted empty keys when a different dimension rejected the request.
                status.setRollbackOnly();
                return new Decision(false, retryAfter);
            }
            for (Bucket bucket : buckets) {
                jdbc.update("""
                        UPDATE rate_limit_buckets SET window_started_at = ?, request_count = ?, expires_at = ?
                        WHERE bucket_key = ?
                        """, bucket.windowStart(), bucket.count() + 1,
                        bucket.windowStart() + bucket.limit().windowSeconds() * 2000, bucket.limit().key());
            }
            return new Decision(true, 0);
        }));
    }
    /** Bounded periodic cleanup is independent of whether an old bucket is visited again. */
    @Scheduled(fixedDelayString = "${platform.rate-limit.cleanup-interval-ms:60000}", initialDelay = 60000)
    public void cleanupExpired() {
        jdbc.update("DELETE FROM rate_limit_buckets WHERE expires_at < ? LIMIT 500", clock.millis());
    }
    public long consume(String key,int limit,long windowSeconds) {
        Decision decision=tryAcquire(List.of(new Limit(key,limit,windowSeconds)));
        return decision.allowed()?0:decision.retryAfterSeconds();
    }
}
