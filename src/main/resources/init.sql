CREATE DATABASE IF NOT EXISTS content_platform
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE content_platform;

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    username    VARCHAR(20) NOT NULL COMMENT 'unique username',
    nickname    VARCHAR(30) NOT NULL COMMENT 'display nickname',
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

CREATE TABLE IF NOT EXISTS event_outbox (
    event_id        VARCHAR(64) NOT NULL COMMENT 'event id',
    aggregate_type  VARCHAR(64) NOT NULL COMMENT 'aggregate type',
    aggregate_id    VARCHAR(64) NOT NULL COMMENT 'aggregate id',
    event_type      VARCHAR(64) NOT NULL COMMENT 'event type',
    payload         LONGTEXT NOT NULL COMMENT 'json payload',
    status          ENUM('PENDING','PUBLISHED','DEAD') NOT NULL DEFAULT 'PENDING' COMMENT 'outbox status',
    retry_count     INT NOT NULL DEFAULT 0 COMMENT 'publish retry count',
    next_retry_at   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'next retry time',
    published_at    DATETIME COMMENT 'published time',
    last_error      VARCHAR(500) COMMENT 'last publish error',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    PRIMARY KEY (event_id),
    KEY idx_event_outbox_type_status_retry (event_type, status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='event outbox';

CREATE TABLE IF NOT EXISTS event_consume_log (
    id              BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    event_id        VARCHAR(64) NOT NULL COMMENT 'event id',
    consumer        VARCHAR(128) NOT NULL COMMENT 'consumer id',
    status          ENUM('PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PROCESSING' COMMENT 'consume status',
    consumed_at     DATETIME COMMENT 'consumed time',
    error_message   VARCHAR(500) COMMENT 'last consume error',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_consume_log (event_id, consumer),
    KEY idx_event_consume_log_consumer_status (consumer, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='event consume log';

CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    user_id         BIGINT NOT NULL COMMENT 'target user id',
    type            VARCHAR(32) NOT NULL COMMENT 'notification type',
    title           VARCHAR(120) NOT NULL COMMENT 'notification title',
    content         VARCHAR(500) NOT NULL COMMENT 'notification content',
    biz_id          BIGINT NOT NULL COMMENT 'related business id',
    read_status     TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'read flag',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    PRIMARY KEY (id),
    KEY idx_notifications_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='notifications';

CREATE TABLE IF NOT EXISTS notification_deliveries (
    id                  BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    notification_id     BIGINT NOT NULL COMMENT 'notification id',
    channel             VARCHAR(32) NOT NULL COMMENT 'delivery channel',
    status              VARCHAR(32) NOT NULL COMMENT 'delivery status',
    retry_count         INT NOT NULL DEFAULT 0 COMMENT 'delivery retry count',
    last_error          VARCHAR(500) COMMENT 'last delivery error',
    sent_at             DATETIME COMMENT 'sent time',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_delivery_channel (notification_id, channel),
    KEY idx_notification_deliveries_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='notification deliveries';

INSERT IGNORE INTO users (username, nickname, email, password, role)
VALUES ('admin', 'admin', 'admin@example.com', '$2b$12$pzkMHEpkHa791fwQIMJoJezNQjWfqfyYyH4PSsiHrLuf6N5s9M3zi', 'ADMIN');

INSERT IGNORE INTO users (username, nickname, email, password, role, avatar_url, signature)
VALUES (
    'demo_author',
    'Demo Author',
    'demo-author@example.com',
    '$2b$12$pzkMHEpkHa791fwQIMJoJezNQjWfqfyYyH4PSsiHrLuf6N5s9M3zi',
    'USER',
    'https://api.dicebear.com/7.x/shapes/svg?seed=demo-author',
    'Local demo content author'
);

INSERT INTO articles (
    author_id,
    title,
    content,
    summary,
    cover_url,
    cover_color,
    word_count,
    read_minutes,
    duration_category,
    status,
    submit_count,
    last_submitted_at,
    published_at,
    deleted
)
SELECT
    u.id,
    'Demo Quick Start: Distributed Refactor',
    'This quick article is seeded for the local demo environment. It explains the gateway, auth, content, review, notification, search, and file service split in a concise way so the home page and category views always have at least one approved article to render.',
    'A seeded QUICK article for the local demo home page.',
    'https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1200&q=80',
    '#1D4ED8',
    62,
    3.5,
    'QUICK',
    'APPROVED',
    1,
    DATE_SUB(NOW(), INTERVAL 3 DAY),
    DATE_SUB(NOW(), INTERVAL 3 DAY),
    0
FROM users u
WHERE u.username = 'demo_author'
  AND NOT EXISTS (
      SELECT 1 FROM articles a WHERE a.title = 'Demo Quick Start: Distributed Refactor'
  );

INSERT INTO articles (
    author_id,
    title,
    content,
    summary,
    cover_url,
    cover_color,
    word_count,
    read_minutes,
    duration_category,
    status,
    submit_count,
    last_submitted_at,
    published_at,
    deleted
)
SELECT
    u.id,
    'Demo Short Read: Event Outbox and Review Flow',
    'This seeded short article walks through how the content service writes to event_outbox, how review-service consumes the submission intent, and how notification-service and search-service observe approved status changes. It is long enough to behave like a realistic article in the interview demo.',
    'A seeded SHORT article describing outbox and review flow.',
    'https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=1200&q=80',
    '#0F766E',
    128,
    6.0,
    'SHORT',
    'APPROVED',
    1,
    DATE_SUB(NOW(), INTERVAL 2 DAY),
    DATE_SUB(NOW(), INTERVAL 2 DAY),
    0
FROM users u
WHERE u.username = 'demo_author'
  AND NOT EXISTS (
      SELECT 1 FROM articles a WHERE a.title = 'Demo Short Read: Event Outbox and Review Flow'
  );

INSERT INTO articles (
    author_id,
    title,
    content,
    summary,
    cover_url,
    cover_color,
    word_count,
    read_minutes,
    duration_category,
    status,
    submit_count,
    last_submitted_at,
    published_at,
    deleted
)
SELECT
    u.id,
    'Demo Deep Dive: Local Delivery and Operations Baseline',
    'This seeded deep article explains the local middleware stack, one-click startup script, service health checks, TraceId propagation, and the minimal smoke test workflow. It exists to keep the DEEP category populated after a fresh database initialization and to give the interview demo a complete home page.',
    'A seeded DEEP article covering the local delivery and operations baseline.',
    'https://images.unsplash.com/photo-1498050108023-c5249f4df085?auto=format&fit=crop&w=1200&q=80',
    '#7C2D12',
    236,
    11.5,
    'DEEP',
    'APPROVED',
    1,
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    0
FROM users u
WHERE u.username = 'demo_author'
  AND NOT EXISTS (
      SELECT 1 FROM articles a WHERE a.title = 'Demo Deep Dive: Local Delivery and Operations Baseline'
  );
