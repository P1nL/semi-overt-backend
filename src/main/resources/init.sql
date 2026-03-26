CREATE DATABASE IF NOT EXISTS content_platform
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE content_platform;

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    username    VARCHAR(20) NOT NULL COMMENT 'unique username',
    email       VARCHAR(100) NOT NULL COMMENT 'unique email',
    password    VARCHAR(100) NOT NULL COMMENT 'bcrypt password hash',
    role        ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER' COMMENT 'user role',
    avatar_url  VARCHAR(255) COMMENT 'avatar url',
    cover_url   VARCHAR(255) COMMENT 'profile cover url',
    signature   VARCHAR(100) COMMENT 'profile signature',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='users';

CREATE TABLE IF NOT EXISTS articles (
    id                  BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    author_id           BIGINT NOT NULL COMMENT 'author id',
    title               VARCHAR(120) COMMENT 'article title',
    content             LONGTEXT COMMENT 'article content snapshot',
    summary             VARCHAR(255) COMMENT 'article summary',
    cover_url           VARCHAR(255) COMMENT 'cover image url',
    cover_color         VARCHAR(16) COMMENT 'cover dominant color',
    word_count          INT NOT NULL DEFAULT 0 COMMENT 'word count',
    read_minutes        DECIMAL(5,1) NOT NULL DEFAULT 0.0 COMMENT 'reading minutes',
    duration_category   ENUM('QUICK','SHORT','DEEP') NOT NULL DEFAULT 'QUICK' COMMENT 'reading duration bucket',
    status              ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL DEFAULT 'DRAFT' COMMENT 'article status',
    submit_count        INT NOT NULL DEFAULT 0 COMMENT 'submit count',
    last_submitted_at   DATETIME COMMENT 'latest submit time',
    published_at        DATETIME COMMENT 'published time',
    deleted             TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_author_id (author_id),
    KEY idx_status (status),
    KEY idx_duration_category (duration_category),
    KEY idx_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='articles';

CREATE TABLE IF NOT EXISTS review_logs (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    article_id  BIGINT NOT NULL COMMENT 'article id',
    operator_id BIGINT NOT NULL COMMENT 'operator id',
    action      ENUM('APPROVE','REJECT','RETURN','CANCEL') NOT NULL COMMENT 'review action',
    from_status ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL COMMENT 'status before action',
    to_status   ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL COMMENT 'status after action',
    reason      VARCHAR(500) COMMENT 'review reason',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    PRIMARY KEY (id),
    KEY idx_article_id (article_id),
    KEY idx_operator_id (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='review logs';

CREATE TABLE IF NOT EXISTS review_tasks (
    id              BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    article_id      BIGINT NOT NULL COMMENT 'article id',
    author_id       BIGINT NOT NULL COMMENT 'article author id',
    title           VARCHAR(120) COMMENT 'article title snapshot',
    word_count      INT NOT NULL DEFAULT 0 COMMENT 'word count snapshot',
    status          ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT 'projection status',
    submit_count    INT NOT NULL DEFAULT 0 COMMENT 'submit count snapshot',
    submitted_at    DATETIME COMMENT 'latest submitted time',
    last_event_id   VARCHAR(128) COMMENT 'latest projection event id',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_tasks_article_id (article_id),
    KEY idx_review_tasks_status_submitted_at (status, submitted_at),
    KEY idx_review_tasks_author_id (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='review pending task projection';

INSERT IGNORE INTO users (username, email, password, role)
VALUES ('admin', 'admin@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'ADMIN');
