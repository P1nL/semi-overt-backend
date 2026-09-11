package com.platform.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.platform.content.config.MybatisPlusConfig;
import com.platform.content.mapper.ArticleMapper;
import com.platform.contract.content.dto.UserProfileArticleStatsDto;
import com.platform.contract.content.dto.WritingCalendarDayDto;
import com.platform.kernel.enums.ArticleStatus;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real MySQL coverage for package-1 public/private aggregation and calendar boundary. */
@EnabledIfEnvironmentVariable(named = "S4_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13306/")
class UserProfileAggregationMySqlTest {

    private static final ZoneId TEST_ZONE = ZoneId.of("Asia/Shanghai");

    private String baseUrl;
    private String password;
    private String schema;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private ArticleMapper articleMapper;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = requiredEnv("S4_MYSQL_URL");
        password = requiredEnv("S4_MYSQL_PASSWORD");
        schema = "s4_profile_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(baseUrl, "root", password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        var dataSource = new DriverManagerDataSource(
                baseUrl + schema + "?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true",
                "root",
                password);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.execute("""
                CREATE TABLE articles (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    author_id BIGINT NOT NULL,
                    title VARCHAR(120),
                    content LONGTEXT,
                    summary VARCHAR(255),
                    cover_url VARCHAR(512),
                    cover_color VARCHAR(32),
                    word_count INT NOT NULL DEFAULT 0,
                    read_minutes DECIMAL(6,1) NOT NULL DEFAULT 0.0,
                    duration_category ENUM('QUICK','SHORT','DEEP') NOT NULL DEFAULT 'QUICK',
                    status ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL DEFAULT 'DRAFT',
                    submit_count INT NOT NULL DEFAULT 0,
                    last_submitted_at DATETIME(6),
                    submission_id VARCHAR(64),
                    published_at DATETIME(6),
                    last_featured_at DATETIME(6),
                    draft_visible BOOLEAN NOT NULL DEFAULT FALSE,
                    version BIGINT NOT NULL DEFAULT 0,
                    deleted TINYINT(1) NOT NULL DEFAULT 0,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (id),
                    KEY idx_profile_author_updated (author_id, updated_at),
                    KEY idx_profile_author_status (author_id, status, deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ArticleMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(new GlobalConfig().setMetaObjectHandler(new MybatisPlusConfig.AutoFillHandler()));
        SqlSessionFactory sessionFactory = factory.getObject();
        articleMapper = new SqlSessionTemplate(sessionFactory).getMapper(ArticleMapper.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (schema == null) {
            return;
        }
        try (var connection = DriverManager.getConnection(baseUrl, "root", password);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + schema);
        }
    }

    @Test
    void publicStatsExcludePrivateWordsAndPrivateCounts() {
        insert(1L, ArticleStatus.APPROVED, 100, LocalDateTime.now());
        insert(2L, ArticleStatus.APPROVED, 2331, LocalDateTime.now());
        insert(3L, ArticleStatus.DRAFT, 498, LocalDateTime.now());
        insert(4L, ArticleStatus.PENDING, 17, LocalDateTime.now());

        UserProfileArticleStatsDto visitor = articleMapper.selectProfileStats(101L, false);
        UserProfileArticleStatsDto owner = articleMapper.selectProfileStats(101L, true);

        assertEquals(2L, visitor.getApproved());
        assertEquals(0L, visitor.getPending());
        assertEquals(0L, visitor.getDraft());
        assertEquals(2431, visitor.getTotalWordCount());
        assertEquals(2L, owner.getApproved());
        assertEquals(1L, owner.getPending());
        assertEquals(1L, owner.getDraft());
        assertEquals(2946, owner.getTotalWordCount());
    }

    @Test
    void calendarIncludesThreeYearBoundaryAtMidnightButExcludesPreviousDay() {
        LocalDate mysqlToday = jdbc.queryForObject(
                "SELECT CURRENT_DATE()",
                (resultSet, rowNum) -> resultSet.getDate(1).toLocalDate());
        LocalDate boundaryDate = mysqlToday.minusYears(3);
        assertEquals(boundaryDate, LocalDate.now(TEST_ZONE).minusYears(3));
        LocalDateTime boundary = boundaryDate.atStartOfDay();
        insert(10L, ArticleStatus.APPROVED, 10, boundary);
        // DATETIME(6) stores microseconds; use exactly the previous day's final microsecond.
        LocalDateTime previousDayLastMicrosecond = boundary.minusNanos(1_000L);
        insert(11L, ArticleStatus.APPROVED, 20, previousDayLastMicrosecond);
        insert(12L, ArticleStatus.DRAFT, 30, boundary.plusHours(1));
        assertEquals(previousDayLastMicrosecond, jdbc.queryForObject(
                "SELECT updated_at FROM articles WHERE id = 11",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toLocalDateTime()));

        List<WritingCalendarDayDto> publicDays = articleMapper.selectApprovedWritingCalendar(101L, boundary);
        List<WritingCalendarDayDto> allDays = articleMapper.selectWritingCalendar(101L, boundary);

        assertEquals(List.of(new WritingCalendarDayDto(boundaryDate, 10L)), publicDays);
        assertEquals(List.of(new WritingCalendarDayDto(boundaryDate, 40L)), allDays);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM articles WHERE updated_at < ?", Integer.class, boundary) > 0);
    }

    private void insert(long id, ArticleStatus status, int words, LocalDateTime updatedAt) {
        jdbc.update("""
                INSERT INTO articles(id, author_id, title, content, summary, word_count,
                    read_minutes, duration_category, status, version, deleted, created_at, updated_at)
                VALUES (?, 101, ?, 'body', 'summary', ?, 1.0, 'QUICK', ?, 0, 0, ?, ?)
                """, id, "article-" + id, words, status.name(), updatedAt, updatedAt);
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing test environment variable: " + name);
        }
        return value;
    }
}
