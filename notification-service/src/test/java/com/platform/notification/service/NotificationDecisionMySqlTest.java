package com.platform.notification.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.event.ArticleStatusChangedEvent;
import com.platform.notification.config.MybatisPlusConfig;
import com.platform.notification.mapper.NotificationDeliveryMapper;
import com.platform.notification.mapper.NotificationMapper;
import com.platform.notification.service.impl.NotificationEventServiceImpl;
import com.platform.notification.service.impl.NotificationQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real InnoDB + production mappers/services. The database is random and
 * owned by this test; the password is supplied only through the test process.
 */
@EnabledIfEnvironmentVariable(named = "S4_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13306/")
class NotificationDecisionMySqlTest {

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private NotificationEventServiceImpl service;
    private NotificationQueryServiceImpl queryService;

    @BeforeEach
    void setup() throws Exception {
        String base = requiredEnv("S4_MYSQL_URL");
        String password = requiredEnv("S4_MYSQL_PASSWORD");
        String db = "s4_notice_" + UUID.randomUUID().toString().replace("-", "");

        try (var connection = DriverManager.getConnection(base, "root", password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        var dataSource = new DriverManagerDataSource(
                base + db + "?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&useSSL=false",
                "root",
                password);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        new ResourceDatabasePopulator(
                new FileSystemResource("../db/migration/V1__baseline_schema.sql"),
                new FileSystemResource("../db-migration/src/main/resources/db/migration/V2__add_last_featured_at_to_articles.sql"),
                new FileSystemResource("../db-migration/src/main/resources/db/migration/V3__sync_foundation.sql"),
                new FileSystemResource("../db-migration/src/main/resources/db/migration/V4__s3_state_consistency.sql"))
                .execute(dataSource);

        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NotificationMapper.class);
        configuration.addMapper(NotificationDeliveryMapper.class);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MybatisPlusConfig.AutoFillHandler()));
        var session = new SqlSessionTemplate(factory.getObject());
        var notificationMapper = session.getMapper(NotificationMapper.class);
        service = new NotificationEventServiceImpl(
                notificationMapper,
                session.getMapper(NotificationDeliveryMapper.class));
        queryService = new NotificationQueryServiceImpl(notificationMapper);
    }

    @Test
    void concurrentDifferentEventIdsForSameDecisionCreateOneNotificationAndDeliveryPair() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(6);
        try {
            var work = new ArrayList<Future<?>>();
            for (int i = 0; i < 12; i++) {
                work.add(pool.submit(() -> {
                    start.await();
                    tx.executeWithoutResult(status -> service.handleArticleStatusChanged(accepted()));
                    return null;
                }));
            }
            start.countDown();
            for (var future : work) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notification_deliveries", Integer.class));
        assertEquals("ARTICLE_REVIEW", jdbc.queryForObject(
                "SELECT type FROM notifications WHERE decision_id = 'same-decision'", String.class));
        assertEquals("文章审核结果", jdbc.queryForObject(
                "SELECT title FROM notifications WHERE decision_id = 'same-decision'", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_deliveries WHERE channel='EMAIL' AND status='PENDING' AND sent_at IS NULL",
                Integer.class));
    }

    @Test
    void rollbackThenReplayAndDeleteDoesNotNotifyAgain() {
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            service.handleArticleStatusChanged(accepted());
            throw new IllegalStateException("simulated crash before commit");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_deliveries", Integer.class));

        tx.executeWithoutResult(status -> service.handleArticleStatusChanged(accepted()));
        var deleted = accepted();
        deleted.setDeleted(true);
        tx.executeWithoutResult(status -> service.handleArticleStatusChanged(deleted));
        var unconfirmed = accepted();
        unconfirmed.setDecisionId(null);
        tx.executeWithoutResult(status -> service.handleArticleStatusChanged(unconfirmed));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notification_deliveries", Integer.class));
    }

    @Test
    void knownLegacyS3DecisionReplayKeepsEnglishPayloadAndDoesNotDuplicate() {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, title, content, biz_id, read_status, decision_id)
                VALUES (12, 'APPROVED', 'Review approved', '"reviewed article" has been approved and published.', 71, 0, 'same-decision')
                """);
        Long notificationId = jdbc.queryForObject(
                "SELECT id FROM notifications WHERE decision_id = 'same-decision'", Long.class);
        jdbc.update("""
                INSERT INTO notification_deliveries
                    (notification_id, channel, status, retry_count, sent_at)
                VALUES (?, 'IN_APP', 'SENT', 0, CURRENT_TIMESTAMP)
                """, notificationId);
        jdbc.update("""
                INSERT INTO notification_deliveries
                    (notification_id, channel, status, retry_count, sent_at)
                VALUES (?, 'EMAIL', 'PENDING', 0, NULL)
                """, notificationId);

        var firstEvent = accepted();
        tx.executeWithoutResult(status -> service.handleArticleStatusChanged(firstEvent));
        // Same eventId replay and then a distinct eventId replay must both be
        // absorbed by the retained decision_id, without rewriting the S3 row.
        tx.executeWithoutResult(status -> service.handleArticleStatusChanged(firstEvent));
        var replayWithAnotherEventId = accepted();
        tx.executeWithoutResult(status -> service.handleArticleStatusChanged(replayWithAnotherEventId));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notification_deliveries", Integer.class));
        assertEquals("APPROVED", jdbc.queryForObject(
                "SELECT type FROM notifications WHERE decision_id = 'same-decision'", String.class));
        assertEquals("Review approved", jdbc.queryForObject(
                "SELECT title FROM notifications WHERE decision_id = 'same-decision'", String.class));
        assertEquals("\"reviewed article\" has been approved and published.", jdbc.queryForObject(
                "SELECT content FROM notifications WHERE decision_id = 'same-decision'", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_deliveries WHERE notification_id = ? AND channel='EMAIL' AND status='PENDING' AND sent_at IS NULL",
                Integer.class, notificationId));
    }

    @Test
    void sameDecisionDifferentAuthorIsRejectedRatherThanSilentlyMerged() {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, title, content, biz_id, read_status, decision_id)
                VALUES (999, 'APPROVED', 'Review approved', '"reviewed article" has been approved and published.', 71, 0, 'same-decision')
                """);

        assertThrows(IllegalStateException.class,
                () -> tx.executeWithoutResult(status -> service.handleArticleStatusChanged(accepted())));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
    }

    @Test
    void sameDecisionDifferentArticleOrResultIsRejectedRatherThanSilentlyMerged() {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, title, content, biz_id, read_status, decision_id)
                VALUES (12, 'APPROVED', 'Review approved', '"reviewed article" has been approved and published.', 71, 0, 'different-article')
                """);
        var differentArticle = accepted();
        differentArticle.setDecisionId("different-article");
        differentArticle.setArticleId(72L);
        assertThrows(IllegalStateException.class,
                () -> tx.executeWithoutResult(status -> service.handleArticleStatusChanged(differentArticle)));

        jdbc.update("""
                INSERT INTO notifications (user_id, type, title, content, biz_id, read_status, decision_id)
                VALUES (12, 'APPROVED', 'Review approved', '"reviewed article" has been approved and published.', 71, 0, 'different-result')
                """);
        var differentResult = accepted();
        differentResult.setDecisionId("different-result");
        differentResult.setAction(ReviewAction.RETURN);
        differentResult.setToStatus(ArticleStatus.RETURNED);
        assertThrows(IllegalStateException.class,
                () -> tx.executeWithoutResult(status -> service.handleArticleStatusChanged(differentResult)));

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
    }

    @Test
    void legacyRowsWithNullBizAndDecisionRemainReadableAndUserScoped() {
        jdbc.update("""
                INSERT INTO notifications (user_id, type, title, content, biz_id, read_status, decision_id)
                VALUES (101, 'LEGACY_TYPE', '历史通知', 'legacy body', NULL, 0, NULL)
                """);
        jdbc.update("""
                INSERT INTO notifications (user_id, type, title, content, biz_id, read_status, decision_id)
                VALUES (202, 'LEGACY_TYPE', '另一个用户', 'not visible to 101', NULL, 0, NULL)
                """);

        var rows = queryService.listForUser(101L, 50);
        assertEquals(1, rows.size());
        assertEquals(101L, rows.get(0).getUserId());
        assertEquals("LEGACY_TYPE", rows.get(0).getType());
        assertEquals("历史通知", rows.get(0).getTitle());
        assertFalse(rows.get(0).getContent().isBlank());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE biz_id IS NULL AND decision_id IS NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = 101 AND biz_id IS NULL AND decision_id IS NULL",
                Integer.class));
    }

    private ArticleStatusChangedEvent accepted() {
        return ArticleStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .articleId(71L)
                .authorId(12L)
                .decisionId("same-decision")
                .submissionId("submission-1")
                .articleVersion(3L)
                .fromStatus(ArticleStatus.PENDING)
                .toStatus(ArticleStatus.APPROVED)
                .deleted(false)
                .adminId(99L)
                .action(ReviewAction.APPROVE)
                .title("reviewed article")
                .build();
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing test environment variable: " + name);
        }
        return value;
    }
}
