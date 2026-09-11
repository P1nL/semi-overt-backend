package com.platform.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import com.platform.review.entity.ReviewCommand;
import com.platform.review.entity.ReviewTask;
import com.platform.review.mapper.ReviewCommandMapper;
import com.platform.review.mapper.ReviewLogMapper;
import com.platform.review.mapper.ReviewTaskMapper;
import com.platform.review.service.impl.ReviewDecisionCoordinatorImpl;
import com.platform.review.service.impl.ReviewTaskServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "S3_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/")
class ReviewStateMySqlTest {

    private static String baseUrl;
    private static String password;
    private static String database;
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager transactionManager;
    private static AuthUserQueryClient authClient;
    private static ReviewTaskMapper taskMapper;
    private static ReviewCommandMapper commandMapper;
    private static ReviewLogMapper logMapper;
    private static ReviewTaskServiceImpl taskService;
    private static ReviewDecisionCoordinatorImpl coordinator;

    @BeforeAll
    static void setUp() throws Exception {
        baseUrl = System.getenv("S3_MYSQL_URL");
        password = System.getenv("S3_MYSQL_PASSWORD");
        database = "s3_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, "root", password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                baseUrl + database + "?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true"
                        + "&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false",
                "root", password);
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        applySchema();

        org.apache.ibatis.session.SqlSessionFactory factory = mybatisFactory(dataSource);
        org.mybatis.spring.SqlSessionTemplate sqlSession = new org.mybatis.spring.SqlSessionTemplate(factory);
        taskMapper = sqlSession.getMapper(ReviewTaskMapper.class);
        commandMapper = sqlSession.getMapper(ReviewCommandMapper.class);
        logMapper = sqlSession.getMapper(ReviewLogMapper.class);

        authClient = mock(AuthUserQueryClient.class);
        when(authClient.listReviewAdmins()).thenReturn(com.platform.kernel.util.Result.ok(List.of(
                UserSummaryDto.builder().id(101L).username("admin101").build(),
                UserSummaryDto.builder().id(102L).username("admin102").build())));
        taskService = new ReviewTaskServiceImpl(taskMapper, authClient, transactionManager);
        EventOutboxService outbox = new EventOutboxService(jdbc, new ObjectMapper(), transactionManager);
        coordinator = new ReviewDecisionCoordinatorImpl(taskMapper, commandMapper, logMapper, outbox, transactionManager);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (database == null) return;
        try (var connection = DriverManager.getConnection(baseUrl, "root", password);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
        }
    }

    @Test
    void finalOldDecisionThenNewSubmissionClearsClaimColumnsInDatabaseAndRejectsLateEvents() {
        long articleId = 1001L;
        String s1 = "submission-s1";
        taskService.upsertTask(upsert(articleId, s1, 10L, ArticleStatus.PENDING, "s1"));
        Long admin = jdbc.queryForObject("SELECT assigned_admin_id FROM review_tasks WHERE article_id=?", Long.class, articleId);
        ReviewCommand command = coordinator.claim(articleId, admin, "decision-s1", "APPROVE", null, s1, 10L);
        coordinator.finalizeAuthoritativeResult(result(command, "FINAL", ArticleStatus.APPROVED, 11L));
        assertThat(jdbc.queryForObject("SELECT decision_id FROM review_tasks WHERE article_id=?", String.class, articleId))
                .isEqualTo("decision-s1");

        taskService.upsertTask(upsert(articleId, "submission-s2", 12L, ArticleStatus.PENDING, "s2"));
        assertThat(jdbc.queryForObject("SELECT decision_id IS NULL FROM review_tasks WHERE article_id=?", Boolean.class, articleId))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT command_state FROM review_tasks WHERE article_id=?", String.class, articleId))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT submission_id FROM review_tasks WHERE article_id=?", String.class, articleId))
                .isEqualTo("submission-s2");
        assertThat(jdbc.queryForObject("SELECT assigned_admin_id FROM review_tasks WHERE article_id=?", Long.class, articleId))
                .isNotNull();

        taskService.removeTask(com.platform.contract.review.dto.ReviewTaskRemoveReq.builder()
                .articleId(articleId).submissionId(s1).articleVersion(11L).lastEventId("late-s1-cancel").build());
        assertThat(jdbc.queryForObject("SELECT submission_id FROM review_tasks WHERE article_id=?", String.class, articleId))
                .isEqualTo("submission-s2");
        assertThat(jdbc.queryForObject("SELECT last_applied_version FROM review_tasks WHERE article_id=?", Long.class, articleId))
                .isEqualTo(12L);
        assertThat(jdbc.queryForObject("SELECT status FROM review_tasks WHERE article_id=?", String.class, articleId))
                .isEqualTo("PENDING");
    }

    @Test
    void staleS1ReviewPageCannotClaimS2AndConcurrentOpposingClaimsCreateOneCommand() throws Exception {
        long articleId = 1002L;
        taskService.upsertTask(upsert(articleId, "submission-s2", 20L, ArticleStatus.PENDING, "s2"));
        Long admin = jdbc.queryForObject("SELECT assigned_admin_id FROM review_tasks WHERE article_id=?", Long.class, articleId);
        assertThatThrownBy(() -> coordinator.claim(articleId, admin, "stale-decision", "APPROVE", null,
                "submission-s1", 18L)).hasMessageContaining("baseline is stale");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_commands WHERE article_id=?", Integer.class, articleId))
                .isZero();

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var approve = executor.submit(() -> {
                start.await();
                try {
                    coordinator.claim(articleId, admin, "decision-approve", "APPROVE", null,
                            "submission-s2", 20L);
                    return "CLAIMED";
                } catch (RuntimeException ex) {
                    return "CONFLICT";
                }
            });
            var reject = executor.submit(() -> {
                start.await();
                try {
                    coordinator.claim(articleId, admin, "decision-reject", "REJECT", "invalid",
                            "submission-s2", 20L);
                    return "CLAIMED";
                } catch (RuntimeException ex) {
                    return "CONFLICT";
                }
            });
            start.countDown();
            assertThat(List.of(approve.get(15, TimeUnit.SECONDS), reject.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CLAIMED", "CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_commands WHERE article_id=?", Integer.class, articleId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE aggregate_type='review' AND aggregate_id IN ('decision-approve','decision-reject')", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void timeoutSameKeyPayloadBindingAndConflictNeverWriteSuccessLog() {
        long articleId = 1003L;
        taskService.upsertTask(upsert(articleId, "submission-timeout", 30L, ArticleStatus.PENDING, "timeout"));
        Long admin = jdbc.queryForObject("SELECT assigned_admin_id FROM review_tasks WHERE article_id=?", Long.class, articleId);
        ReviewCommand first = coordinator.claim(articleId, admin, "decision-timeout", "RETURN", "revise",
                "submission-timeout", 30L);
        ReviewCommand retry = coordinator.claim(articleId, admin, "decision-timeout", "RETURN", "revise",
                "submission-timeout", 30L);
        assertThat(retry.getDecisionId()).isEqualTo(first.getDecisionId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_commands WHERE decision_id='decision-timeout'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id='decision-timeout'", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> coordinator.claim(articleId, admin, "decision-timeout", "REJECT", "different",
                "submission-timeout", 30L)).isInstanceOf(com.platform.kernel.exception.BusinessException.class);

        coordinator.finalizeAuthoritativeResult(result(first, "CONFLICT", ArticleStatus.DRAFT, 31L));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_logs WHERE decision_id='decision-timeout'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM review_commands WHERE decision_id='decision-timeout'", String.class))
                .isEqualTo("CONFLICT");
    }

    private static com.platform.contract.review.dto.ReviewTaskUpsertReq upsert(
            long articleId, String submissionId, long version, ArticleStatus status, String eventId) {
        return com.platform.contract.review.dto.ReviewTaskUpsertReq.builder()
                .articleId(articleId).authorId(7L).title("article-" + articleId).wordCount(321)
                .status(status).submitCount(1).submittedAt(LocalDateTime.parse("2026-09-10T10:00:00"))
                .lastEventId(eventId).submissionId(submissionId).articleVersion(version).build();
    }

    private static ReviewDecisionResultDto result(ReviewCommand command, String state,
                                                   ArticleStatus status, long version) {
        return ReviewDecisionResultDto.builder()
                .decisionId(command.getDecisionId()).articleId(command.getArticleId())
                .submissionId(command.getSubmissionId()).state(state).status(status).version(version)
                .updatedAt(LocalDateTime.parse("2026-09-10T10:05:00"))
                .adminId(command.getAdminId()).action(command.getAction()).reason(command.getReason()).build();
    }

    private static org.apache.ibatis.session.SqlSessionFactory mybatisFactory(javax.sql.DataSource dataSource) throws Exception {
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment(
                "test", new org.mybatis.spring.transaction.SpringManagedTransactionFactory(), dataSource);
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        com.baomidou.mybatisplus.core.config.GlobalConfig global = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        global.setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig());
        global.setMetaObjectHandler(new com.platform.review.config.MybatisPlusConfig.AutoFillHandler());
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(configuration, global);
        configuration.addMapper(ReviewTaskMapper.class);
        configuration.addMapper(ReviewCommandMapper.class);
        configuration.addMapper(ReviewLogMapper.class);
        return new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static void applySchema() throws Exception {
        String v1 = Files.readString(Path.of("../db/migration/V1__baseline_schema.sql"), StandardCharsets.UTF_8);
        String v3 = Files.readString(Path.of("../db-migration/src/main/resources/db/migration/V3__sync_foundation.sql"), StandardCharsets.UTF_8);
        String v4 = Files.readString(Path.of("../db-migration/src/main/resources/db/migration/V4__s3_state_consistency.sql"), StandardCharsets.UTF_8);
        for (String sql : List.of(v1, v3, v4)) {
            for (String statement : sql.replaceAll("(?m)^--.*$", "").split(";")) {
                if (!statement.isBlank()) jdbc.execute(statement);
            }
        }
    }
}
