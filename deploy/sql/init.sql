CREATE DATABASE IF NOT EXISTS content_platform
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE content_platform;

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    username    VARCHAR(32) NOT NULL COMMENT 'unique username',
    nickname    VARCHAR(60) COMMENT 'display nickname',
    email       VARCHAR(120) NOT NULL COMMENT 'unique email',
    password    VARCHAR(120) NOT NULL COMMENT 'bcrypt password hash',
    role        ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER' COMMENT 'user role',
    avatar_url  VARCHAR(255) COMMENT 'avatar url',
    cover_url   VARCHAR(512) COMMENT 'profile cover url',
    signature   VARCHAR(100) COMMENT 'profile signature',
    session_version BIGINT NOT NULL DEFAULT 0 COMMENT 'session invalidation version',
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
    cover_url           VARCHAR(512) COMMENT 'cover image url',
    cover_color         VARCHAR(32) COMMENT 'cover dominant color',
    word_count          INT NOT NULL DEFAULT 0 COMMENT 'word count',
    read_minutes        DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT 'reading minutes',
    duration_category   ENUM('QUICK','SHORT','DEEP') NOT NULL DEFAULT 'QUICK' COMMENT 'reading duration bucket',
    status              ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL DEFAULT 'DRAFT' COMMENT 'article status',
    submit_count        INT NOT NULL DEFAULT 0 COMMENT 'submit count',
    last_submitted_at   DATETIME COMMENT 'latest submit time',
    published_at        DATETIME COMMENT 'published time',
    last_featured_at    DATETIME NULL DEFAULT NULL COMMENT 'last time this article was shown on home hero',
    draft_visible       BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'whether a draft is visible',
    version             BIGINT NOT NULL DEFAULT 0 COMMENT 'optimistic state version',
    submission_id       VARCHAR(64) COMMENT 'current review submission id',
    deleted             TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'logical delete flag',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_author_id (author_id),
    KEY idx_status (status),
    KEY idx_duration_category (duration_category),
    KEY idx_published_at (published_at),
    KEY idx_articles_featured_status (status, last_featured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='articles';

CREATE TABLE IF NOT EXISTS review_logs (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    article_id  BIGINT NOT NULL COMMENT 'article id',
    operator_id BIGINT NOT NULL COMMENT 'operator id',
    action      ENUM('APPROVE','REJECT','RETURN','CANCEL') NOT NULL COMMENT 'review action',
    from_status ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') COMMENT 'status before action',
    to_status   ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') COMMENT 'status after action',
    reason      VARCHAR(500) COMMENT 'review reason',
    decision_id VARCHAR(64) COMMENT 'idempotent review decision id',
    submission_id VARCHAR(64) COMMENT 'review submission id',
    article_version BIGINT COMMENT 'article version after decision',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_log_decision (decision_id),
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
    assigned_admin_id BIGINT COMMENT 'assigned reviewer id',
    submission_id   VARCHAR(64) COMMENT 'review submission id',
    last_applied_version BIGINT NOT NULL DEFAULT -1 COMMENT 'latest projected article version',
    decision_id     VARCHAR(64) COMMENT 'current review decision id',
    command_state   VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'review command state',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_tasks_article_id (article_id),
    KEY idx_review_tasks_status_submitted_at (status, submitted_at),
    KEY idx_review_tasks_author_id (author_id),
    KEY idx_review_assignment (assigned_admin_id, status, submitted_at)
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
    lease_owner     VARCHAR(128) COMMENT 'current publisher lease owner',
    lease_token     VARCHAR(64) COMMENT 'current publisher lease token',
    lease_until     DATETIME(6) COMMENT 'publisher lease expiration time',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
    PRIMARY KEY (event_id),
    KEY idx_event_outbox_type_status_retry (event_type, status, next_retry_at),
    KEY idx_event_outbox_lease (status, lease_until, next_retry_at)
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
    biz_id          BIGINT COMMENT 'related business id',
    decision_id     VARCHAR(64) COMMENT 'review decision id',
    read_status     TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'read flag',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_decision (decision_id),
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


CREATE TABLE IF NOT EXISTS rate_limit_buckets (
    bucket_key          VARCHAR(160) NOT NULL,
    window_started_at   BIGINT NOT NULL,
    request_count       BIGINT NOT NULL,
    expires_at          BIGINT NOT NULL,
    PRIMARY KEY (bucket_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='distributed rate limit buckets';

CREATE TABLE IF NOT EXISTS auth_device_sessions (
    session_id          VARCHAR(64) NOT NULL,
    user_id             BIGINT NOT NULL,
    family_id           VARCHAR(64) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    last_used_at        TIMESTAMP NOT NULL,
    idle_expires_at     TIMESTAMP NOT NULL,
    absolute_expires_at TIMESTAMP NOT NULL,
    persistent          BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at          TIMESTAMP NULL,
    PRIMARY KEY (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='authentication device sessions';

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    token_hash          VARCHAR(64) NOT NULL,
    session_id          VARCHAR(64) NOT NULL,
    family_id           VARCHAR(64) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    consumed_at         TIMESTAMP NULL,
    replaced_by_hash    VARCHAR(64) NULL,
    PRIMARY KEY (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='refresh token rotation records';

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token               VARCHAR(64) NOT NULL,
    user_id             BIGINT NOT NULL,
    code_salt           VARCHAR(64) NOT NULL,
    code_hash           VARCHAR(128) NOT NULL,
    attempts            INT NOT NULL DEFAULT 0,
    expires_at          TIMESTAMP NOT NULL,
    used_at             TIMESTAMP NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='password reset tokens';

CREATE TABLE IF NOT EXISTS email_verification_codes (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    email               VARCHAR(120) NOT NULL,
    purpose             VARCHAR(32) NOT NULL,
    code_salt           VARCHAR(64) NOT NULL,
    code_hash           VARCHAR(128) NOT NULL,
    attempts            INT NOT NULL DEFAULT 0,
    expires_at          TIMESTAMP NOT NULL,
    used_at             TIMESTAMP NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='email verification codes';

CREATE TABLE IF NOT EXISTS home_article_exposures (
    user_id             BIGINT NOT NULL,
    article_id          BIGINT NOT NULL,
    first_exposed_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_exposed_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exposure_count      INT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='per-user home article exposures';

CREATE TABLE IF NOT EXISTS content_author_locks (
    author_id           BIGINT NOT NULL,
    PRIMARY KEY (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='content author write locks';

CREATE TABLE IF NOT EXISTS content_review_decisions (
    decision_id         VARCHAR(64) NOT NULL,
    article_id          BIGINT NOT NULL,
    submission_id       VARCHAR(64) NOT NULL,
    expected_version    BIGINT NOT NULL,
    admin_id            BIGINT NOT NULL,
    action              VARCHAR(16) NOT NULL,
    reason              TEXT NULL,
    state               VARCHAR(16) NOT NULL,
    status              VARCHAR(16) NULL,
    article_version     BIGINT NULL,
    updated_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (decision_id),
    KEY idx_content_decision_submission (article_id, submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='content-side review decision receipts';

CREATE TABLE IF NOT EXISTS review_commands (
    decision_id         VARCHAR(64) NOT NULL,
    article_id          BIGINT NOT NULL,
    submission_id       VARCHAR(64) NOT NULL,
    expected_version    BIGINT NOT NULL,
    admin_id            BIGINT NOT NULL,
    action              VARCHAR(16) NOT NULL,
    reason              TEXT NULL,
    payload_hash        VARCHAR(64) NOT NULL,
    state               VARCHAR(16) NOT NULL,
    status              VARCHAR(16) NULL,
    article_version     BIGINT NULL,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    error_message       VARCHAR(512) NULL,
    PRIMARY KEY (decision_id),
    UNIQUE KEY uk_review_command_submission (article_id, submission_id),
    KEY idx_review_command_state_updated (state, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='durable review commands';

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
