package com.platform.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ReviewDecisionResultDto;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.client.ReviewTaskClient;
import com.platform.content.api.req.SaveDraftReq;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.mapper.ContentReviewDecisionMapper;
import com.platform.content.service.impl.ArticleServiceImpl;
import com.platform.content.service.impl.DraftServiceImpl;
import com.platform.events.support.EventOutboxService;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.sql.Statement;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "S3_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13306/")
class ContentStateMySqlConcurrencyTest {

    private static String baseUrl;
    private static String username;
    private static String password;
    private static String schema;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static ArticleMapper articleMapper;
    private static ContentReviewDecisionMapper decisionMapper;
    private static EventOutboxService outbox;
    private static ReviewDecisionService decisions;
    private static ArticleServiceImpl articles;
    private static DraftServiceImpl drafts;

    @BeforeAll
    static void createIsolatedSchema() throws Exception {
        baseUrl = System.getenv("S3_MYSQL_URL");
        username = environmentOrDefault("S3_MYSQL_USERNAME", "root");
        password = System.getenv("S3_MYSQL_PASSWORD");
        schema = "s3_content_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                baseUrl + schema + "?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
                username,
                password
        );
        jdbc = new JdbcTemplate(dataSource);
        createTables();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactions = new TransactionTemplate(transactionManager);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ArticleMapper.class);
        configuration.addMapper(ContentReviewDecisionMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory sessionFactory = factoryBean.getObject();
        assertNotNull(sessionFactory);
        SqlSessionTemplate session = new SqlSessionTemplate(sessionFactory);
        articleMapper = session.getMapper(ArticleMapper.class);
        decisionMapper = session.getMapper(ContentReviewDecisionMapper.class);

        outbox = new EventOutboxService(jdbc, new ObjectMapper().findAndRegisterModules(), transactionManager);
        HomeService home = mock(HomeService.class);
        decisions = new ReviewDecisionService(articleMapper, decisionMapper, outbox, home);
        AuthUserQueryClient auth = mock(AuthUserQueryClient.class);
        ReviewReasonClient reasons = mock(ReviewReasonClient.class);
        ReviewTaskClient tasks = mock(ReviewTaskClient.class);
        articles = new ArticleServiceImpl(articleMapper, auth, reasons, tasks, outbox, decisions);
        drafts = new DraftServiceImpl(articleMapper, reasons);
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
    void concurrentDraftSaveWithSameVersionHasOneWinner() throws Exception {
        reset();
        long articleId = seedArticle(10L, ArticleStatus.DRAFT, 0L, null, "old body", false);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final int candidate = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    SaveDraftReq request = new SaveDraftReq();
                    request.setVersion(0L);
                    request.setContent("candidate-" + candidate);
                    try {
                        transactions.executeWithoutResult(status -> drafts.saveDraft(articleId, 10L, request));
                        return true;
                    } catch (RuntimeException conflict) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int success = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) success++;
            }
            assertEquals(1, success);
            assertEquals(1L, jdbc.queryForObject("SELECT version FROM articles WHERE id=?", Long.class, articleId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentDraftCreationNeverExceedsOneHundred() throws Exception {
        reset();
        for (int i = 0; i < 99; i++) {
            seedArticle(20L, ArticleStatus.DRAFT, 0L, null, "draft-" + i, false);
        }
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        transactions.executeWithoutResult(status -> articles.createArticle(20L));
                        return true;
                    } catch (RuntimeException conflict) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int success = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) success++;
            }
            assertEquals(1, success);
            assertEquals(100, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM articles WHERE author_id=20 AND deleted=0", Integer.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void authorCanDeleteApprovedArticleWithCasAndVersionedEvent() {
        reset();
        long articleId = seedArticle(30L, ArticleStatus.APPROVED, 4L, "published-submission", "published", false);

        transactions.executeWithoutResult(status -> articles.deleteArticle(articleId, 30L));

        var row = jdbc.queryForMap("SELECT deleted,version,status FROM articles WHERE id=?", articleId);
        assertEquals(true, row.get("deleted"));
        assertEquals(5L, ((Number) row.get("version")).longValue());
        String payload = jdbc.queryForObject("SELECT payload FROM event_outbox", String.class);
        assertNotNull(payload);
        assertTrue(payload.contains("\"articleVersion\":5"));
        assertTrue(payload.contains("\"deleted\":true"));
    }

    @Test
    void concurrentSameDecisionIdReturnsOneFinalResultWithoutDuplicateEvent() throws Exception {
        reset();
        long articleId = seedArticle(40L, ArticleStatus.PENDING, 7L, "submission-same", "pending", false);
        ApplyReviewResultReq request = command("decision-same", "submission-same", 7L, 900L, ReviewAction.APPROVE, null);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ReviewDecisionResultDto>> futures = List.of(
                    executor.submit(() -> applyAfter(start, articleId, request)),
                    executor.submit(() -> applyAfter(start, articleId, request))
            );
            start.countDown();
            Set<String> states = new HashSet<>();
            Set<Long> versions = new HashSet<>();
            for (Future<ReviewDecisionResultDto> future : futures) {
                ReviewDecisionResultDto result = future.get(15, TimeUnit.SECONDS);
                states.add(result.getState());
                versions.add(result.getVersion());
            }
            assertEquals(Set.of("FINAL"), states);
            assertEquals(Set.of(8L), versions);
            assertEquals(1, count("SELECT COUNT(*) FROM content_review_decisions"));
            assertEquals(1, count("SELECT COUNT(*) FROM event_outbox"));
            assertEquals("APPROVED", jdbc.queryForObject(
                    "SELECT status FROM articles WHERE id=?", String.class, articleId));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void differentKeysForSameSubmissionYieldOneFinalAndOneConflict() throws Exception {
        reset();
        long articleId = seedArticle(50L, ArticleStatus.PENDING, 11L, "submission-race", "pending", false);
        ApplyReviewResultReq approve = command("decision-a", "submission-race", 11L, 901L,
                ReviewAction.APPROVE, null);
        ApplyReviewResultReq reject = command("decision-b", "submission-race", 11L, 902L,
                ReviewAction.REJECT, "not suitable");
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReviewDecisionResultDto> a = executor.submit(() -> applyAfter(start, articleId, approve));
            Future<ReviewDecisionResultDto> b = executor.submit(() -> applyAfter(start, articleId, reject));
            start.countDown();
            Set<String> states = Set.of(a.get(15, TimeUnit.SECONDS).getState(),
                    b.get(15, TimeUnit.SECONDS).getState());
            assertEquals(Set.of("FINAL", "CONFLICT"), states);
            assertEquals(2, count("SELECT COUNT(*) FROM content_review_decisions"));
            assertEquals(1, count("SELECT COUNT(*) FROM event_outbox"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void conflictKeyDoesNotBlockLaterLegitimateDecision() {
        reset();
        long articleId = seedArticle(60L, ArticleStatus.PENDING, 13L, "submission-valid", "pending", false);
        ReviewDecisionResultDto stale = transactions.execute(status -> decisions.apply(articleId,
                command("decision-stale", "old-submission", 12L, 903L, ReviewAction.APPROVE, null)));
        ReviewDecisionResultDto accepted = transactions.execute(status -> decisions.apply(articleId,
                command("decision-valid", "submission-valid", 13L, 904L, ReviewAction.RETURN, "revise")));

        assertNotNull(stale);
        assertNotNull(accepted);
        assertEquals("CONFLICT", stale.getState());
        assertEquals("FINAL", accepted.getState());
        assertEquals(2, count("SELECT COUNT(*) FROM content_review_decisions"));
        assertEquals("RETURNED", jdbc.queryForObject("SELECT status FROM articles WHERE id=?", String.class, articleId));
    }

    @Test
    void sameDecisionIdWithDifferentPayloadIsRejectedWithoutChangingFinalResult() {
        reset();
        long articleId = seedArticle(70L, ArticleStatus.PENDING, 3L, "submission-binding", "pending", false);
        ApplyReviewResultReq original = command("decision-binding", "submission-binding", 3L, 905L,
                ReviewAction.APPROVE, null);
        transactions.execute(status -> decisions.apply(articleId, original));

        ApplyReviewResultReq changed = command("decision-binding", "submission-binding", 3L, 905L,
                ReviewAction.REJECT, "changed");
        assertThrows(RuntimeException.class,
                () -> transactions.execute(status -> decisions.apply(articleId, changed)));
        assertEquals("FINAL", jdbc.queryForObject(
                "SELECT state FROM content_review_decisions WHERE decision_id='decision-binding'", String.class));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM articles WHERE id=?", String.class, articleId));
    }

    private static ReviewDecisionResultDto applyAfter(CountDownLatch start,
                                                       long articleId,
                                                       ApplyReviewResultReq request) throws Exception {
        start.await();
        return transactions.execute(status -> decisions.apply(articleId, request));
    }

    private static ApplyReviewResultReq command(String decisionId,
                                                 String submissionId,
                                                 Long expectedVersion,
                                                 Long adminId,
                                                 ReviewAction action,
                                                 String reason) {
        ApplyReviewResultReq request = new ApplyReviewResultReq();
        request.setDecisionId(decisionId);
        request.setSubmissionId(submissionId);
        request.setExpectedVersion(expectedVersion);
        request.setAdminId(adminId);
        request.setAction(action);
        request.setReason(reason);
        return request;
    }

    private static long seedArticle(long authorId,
                                    ArticleStatus status,
                                    long version,
                                    String submissionId,
                                    String content,
                                    boolean deleted) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO articles(
                        author_id,title,content,summary,word_count,read_minutes,duration_category,
                        status,submit_count,last_submitted_at,submission_id,draft_visible,version,deleted,
                        created_at,updated_at
                    ) VALUES(?,?,?,?,0,0.0,'QUICK',?,1,CURRENT_TIMESTAMP(6),?,0,?,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, authorId);
            statement.setString(2, "title");
            statement.setString(3, content);
            statement.setString(4, "summary");
            statement.setString(5, status.name());
            statement.setString(6, submissionId);
            statement.setLong(7, version);
            statement.setBoolean(8, deleted);
            return statement;
        }, key);
        if (key.getKey() == null) {
            throw new IllegalStateException("article insert returned no generated key");
        }
        return key.getKey().longValue();
    }

    private static int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static void reset() {
        jdbc.update("DELETE FROM event_outbox");
        jdbc.update("DELETE FROM content_review_decisions");
        jdbc.update("DELETE FROM articles");
        jdbc.update("DELETE FROM content_author_locks");
    }

    private static void createTables() throws Exception {
        Path repository = Path.of(System.getProperty("maven.multiModuleProjectDirectory", "."))
                .toAbsolutePath().normalize();
        while (repository != null
                && (!Files.exists(repository.resolve("db/migration/V1__baseline_schema.sql"))
                || !Files.exists(repository.resolve(
                        "db-migration/src/main/resources/db/migration/V4__s3_state_consistency.sql")))) {
            repository = repository.getParent();
        }
        if (repository == null) {
            throw new IllegalStateException("Repository migration root not found");
        }
        executeMigration(repository.resolve("db/migration/V1__baseline_schema.sql"));
        executeMigration(repository.resolve(
                "db-migration/src/main/resources/db/migration/V2__add_last_featured_at_to_articles.sql"));
        executeMigration(repository.resolve(
                "db-migration/src/main/resources/db/migration/V3__sync_foundation.sql"));
        executeMigration(repository.resolve(
                "db-migration/src/main/resources/db/migration/V4__s3_state_consistency.sql"));
    }

    private static void executeMigration(Path migration) throws Exception {
        String sql = Files.readString(migration, java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*--.*$", "");
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement.trim());
            }
        }
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}