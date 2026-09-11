package com.platform.events.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.events.entity.EventOutbox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "S3_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13306/")
class PlatformEventsMySqlTest {

    private static String baseUrl;
    private static String username;
    private static String password;
    private static String schema;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static PlatformTransactionManager transactions;
    private static EventOutboxService outbox;
    private static EventConsumeService inbox;

    @BeforeAll
    static void createIsolatedSchema() throws Exception {
        baseUrl = System.getenv("S3_MYSQL_URL");
        username = environmentOrDefault("S3_MYSQL_USERNAME", "root");
        password = System.getenv("S3_MYSQL_PASSWORD");
        schema = "s3_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(
                baseUrl + schema + "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
                username,
                password
        );
        dataSource = ds;
        jdbc = new JdbcTemplate(ds);
        transactions = new DataSourceTransactionManager(ds);
        createTables(jdbc);
        outbox = new EventOutboxService(jdbc, new ObjectMapper(), transactions);
        inbox = new EventConsumeService(jdbc, transactions);
    }

    @AfterAll
    static void dropIsolatedSchema() throws Exception {
        if (schema == null) {
            return;
        }
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
        }
    }

    @Test
    void dualPublishersClaimDisjointRowsWithinAggregateNamespace() throws Exception {
        resetTables();
        insertOutboxRows("article", "ArticleSubmittedEvent", 40);
        insertOutboxRows("review", "ArticleSubmittedEvent", 3);

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<EventOutbox>> first = executor.submit(() -> claimUntilEmpty(start, "publisher-a"));
            Future<List<EventOutbox>> second = executor.submit(() -> claimUntilEmpty(start, "publisher-b"));
            start.countDown();
            List<EventOutbox> a = first.get(20, TimeUnit.SECONDS);
            List<EventOutbox> b = second.get(20, TimeUnit.SECONDS);
            Set<String> ids = new HashSet<>();
            a.forEach(row -> assertTrue(ids.add(row.getEventId())));
            b.forEach(row -> assertTrue(ids.add(row.getEventId())));
            assertEquals(40, ids.size());
            assertEquals(40, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM event_outbox WHERE aggregate_type='article' AND lease_owner IS NOT NULL",
                    Integer.class));
            assertEquals(3, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM event_outbox WHERE aggregate_type='review' AND lease_owner IS NULL",
                    Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<EventOutbox> claimUntilEmpty(CountDownLatch start, String owner) throws Exception {
        start.await();
        List<EventOutbox> claimed = new ArrayList<>();
        while (true) {
            EventOutbox next = outbox.claimNextPublishable(
                    "article", List.of("ArticleSubmittedEvent"), owner, Duration.ofSeconds(30));
            if (next == null) {
                return claimed;
            }
            claimed.add(next);
        }
    }
    @Test
    void expiredLeaseIsReclaimedAndOldTokenIsFenced() throws Exception {
        resetTables();
        insertOutboxRows("article", "ArticleSubmittedEvent", 1);
        EventOutbox oldClaim = outbox.claimNextPublishable("article", List.of("ArticleSubmittedEvent"),
                "old-owner", Duration.ofMillis(120));
        assertTrue(oldClaim != null);
        Thread.sleep(180);
        EventOutbox newClaim = outbox.claimNextPublishable("article", List.of("ArticleSubmittedEvent"),
                "new-owner", Duration.ofSeconds(10));
        assertTrue(newClaim != null);
        assertNotEquals(oldClaim.getLeaseToken(), newClaim.getLeaseToken());
        assertFalse(outbox.markPublished(oldClaim));
        assertFalse(outbox.markRetry(oldClaim, "late old worker"));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM event_outbox WHERE event_id=?", String.class, oldClaim.getEventId()));
        assertEquals("new-owner", jdbc.queryForObject(
                "SELECT lease_owner FROM event_outbox WHERE event_id=?", String.class, oldClaim.getEventId()));
        assertTrue(outbox.markPublished(newClaim));
        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT status FROM event_outbox WHERE event_id=?", String.class, oldClaim.getEventId()));
    }

    @Test
    void producerOutboxWriteRollsBackWithBusinessTransactionOnSameDataSource() {
        resetTables();
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO test_side_effect(event_id) VALUES('producer-business')");
            outbox.saveEvent(
                    "article",
                    "42",
                    "ArticleSubmittedEvent",
                    com.platform.kernel.event.ArticleSubmittedEvent.builder()
                            .eventId("producer-event")
                            .articleId(42L)
                            .build()
            );
            throw new IllegalStateException("rollback producer transaction");
        }));
        assertEquals(0, count("SELECT COUNT(*) FROM test_side_effect"));
        assertEquals(0, count("SELECT COUNT(*) FROM event_outbox"));
    }
    @Test
    void handlerRollbackRemovesInboxAttemptAndBusinessSideEffect() throws Exception {
        resetTables();
        assertThrows(IllegalStateException.class, () -> inbox.executeInTransaction(
                "event-rollback", "consumer-rollback", () -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    jdbc.update("INSERT INTO test_side_effect(event_id) VALUES(?)", "event-rollback");
                    throw new IllegalStateException("rollback me");
                }));
        assertEquals(0, count("SELECT COUNT(*) FROM event_consume_log"));
        assertEquals(0, count("SELECT COUNT(*) FROM test_side_effect"));

        assertEquals(EventConsumeService.ConsumptionResult.HANDLED,
                inbox.executeInTransaction("event-rollback", "consumer-rollback",
                        () -> jdbc.update("INSERT INTO test_side_effect(event_id) VALUES(?)", "event-rollback")));
        assertEquals(1, count("SELECT COUNT(*) FROM event_consume_log WHERE status='SUCCESS'"));
        assertEquals(1, count("SELECT COUNT(*) FROM test_side_effect"));
    }

    @Test
    void concurrentSameEventExecutesSideEffectOnceWithoutDeadlock() throws Exception {
        resetTables();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger handlerEntries = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(12);
        try {
            List<Future<EventConsumeService.ConsumptionResult>> futures = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return inbox.executeInTransaction("event-concurrent", "consumer-concurrent", () -> {
                        handlerEntries.incrementAndGet();
                        jdbc.update("INSERT INTO test_side_effect(event_id) VALUES(?)", "event-concurrent");
                        Thread.sleep(30);
                    });
                }));
            }
            start.countDown();
            int handled = 0;
            int duplicates = 0;
            for (Future<EventConsumeService.ConsumptionResult> future : futures) {
                EventConsumeService.ConsumptionResult result = future.get(20, TimeUnit.SECONDS);
                if (result == EventConsumeService.ConsumptionResult.HANDLED) {
                    handled++;
                } else {
                    duplicates++;
                }
            }
            assertEquals(1, handled);
            assertEquals(23, duplicates);
            assertEquals(1, handlerEntries.get());
            assertEquals(1, count("SELECT COUNT(*) FROM test_side_effect WHERE event_id='event-concurrent'"));
            assertEquals("SUCCESS", jdbc.queryForObject(
                    "SELECT status FROM event_consume_log WHERE event_id='event-concurrent'", String.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void transactionalHandlerProxyJoinsTheSameLocalTransaction() throws Exception {
        resetTables();
        TransactionalHandler target = new TransactionalHandler(jdbc);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactions,
                new AnnotationTransactionAttributeSource()
        ));
        TransactionalHandler proxy = (TransactionalHandler) proxyFactory.getProxy();

        assertThrows(IllegalArgumentException.class, () -> inbox.executeInTransaction(
                "event-proxy", "consumer-proxy", proxy::failAfterInsert));
        assertEquals(0, count("SELECT COUNT(*) FROM event_consume_log"));
        assertEquals(0, count("SELECT COUNT(*) FROM test_side_effect"));

        assertEquals(EventConsumeService.ConsumptionResult.HANDLED,
                inbox.executeInTransaction("event-proxy", "consumer-proxy", proxy::insert));
        assertEquals(1, count("SELECT COUNT(*) FROM test_side_effect"));
    }

    @Test
    void identityLengthIsRejectedBeforeMySqlCanSilentlyTruncate() {
        resetTables();
        assertThrows(RuntimeException.class, () -> inbox.executeInTransaction(
                "x".repeat(65), "consumer", () -> { }));
        assertThrows(RuntimeException.class, () -> inbox.executeInTransaction(
                "event", "x".repeat(129), () -> { }));
        assertEquals(0, count("SELECT COUNT(*) FROM event_consume_log"));
    }

    private static void resetTables() {
        jdbc.update("DELETE FROM test_side_effect");
        jdbc.update("DELETE FROM event_consume_log");
        jdbc.update("DELETE FROM event_outbox");
    }

    private static int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static void insertOutboxRows(String aggregateType, String eventType, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO event_outbox(
                        event_id,aggregate_type,aggregate_id,event_type,payload,status,retry_count,
                        next_retry_at,created_at,updated_at
                    ) VALUES(?,?,?,?,?,'PENDING',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
                    """,
                    UUID.randomUUID().toString(), aggregateType, Integer.toString(i), eventType,
                    "{\"eventId\":\"" + UUID.randomUUID() + "\"}");
        }
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE event_outbox (
                    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
                    aggregate_type VARCHAR(64) NOT NULL,
                    aggregate_id VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    payload LONGTEXT NOT NULL,
                    status ENUM('PENDING','PUBLISHED','DEAD') NOT NULL DEFAULT 'PENDING',
                    retry_count INT NOT NULL DEFAULT 0,
                    next_retry_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                    published_at DATETIME(6) NULL,
                    last_error VARCHAR(500) NULL,
                    lease_owner VARCHAR(128) NULL,
                    lease_token VARCHAR(64) NULL,
                    lease_until DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    KEY idx_event_outbox_type_status_retry(event_type,status,next_retry_at),
                    KEY idx_event_outbox_lease(aggregate_type,status,lease_until,next_retry_at)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE event_consume_log (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(64) NOT NULL,
                    consumer VARCHAR(128) NOT NULL,
                    status ENUM('PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PROCESSING',
                    consumed_at DATETIME(6) NULL,
                    error_message VARCHAR(500) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                    UNIQUE KEY uk_event_consume_log(event_id,consumer)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE test_side_effect (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(64) NOT NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                ) ENGINE=InnoDB
                """);
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static class TransactionalHandler {
        private final JdbcTemplate jdbc;

        public TransactionalHandler(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void insert() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("INSERT INTO test_side_effect(event_id) VALUES('event-proxy')");
        }

        @Transactional
        public void failAfterInsert() {
            insert();
            throw new IllegalArgumentException("proxy rollback");
        }
    }
}