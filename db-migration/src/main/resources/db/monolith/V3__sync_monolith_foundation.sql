-- S1 monolith route: migrate the exact monolith baseline in place.
-- The main migration entry must select MONOLITH with baseline 2 and S1_MONOLITH
-- after its exact source-schema preflight. Flyway executes this version once for
-- the selected db/monolith location; this script is intentionally non-idempotent.

ALTER TABLE users
    RENAME COLUMN password_hash TO password;

ALTER TABLE users
    MODIFY COLUMN signature VARCHAR(100) NULL;

ALTER TABLE articles
    MODIFY COLUMN content LONGTEXT,
    ADD COLUMN last_featured_at DATETIME NULL DEFAULT NULL;

CREATE INDEX idx_articles_featured_status
    ON articles (status, last_featured_at);

ALTER TABLE review_tasks
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    ADD COLUMN last_event_id VARCHAR(128) NULL,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE review_logs
    ADD COLUMN from_status ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NULL,
    ADD COLUMN to_status ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NULL;

ALTER TABLE notifications
    ADD COLUMN biz_id BIGINT NULL,
    ADD COLUMN read_status TINYINT(1) NOT NULL DEFAULT 0;

CREATE TABLE event_outbox (
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
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    KEY idx_event_outbox_type_status_retry (event_type, status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='event outbox';

CREATE TABLE event_consume_log (
    id              BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    event_id        VARCHAR(64) NOT NULL COMMENT 'event id',
    consumer        VARCHAR(128) NOT NULL COMMENT 'consumer id',
    status          ENUM('PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PROCESSING' COMMENT 'consume status',
    consumed_at     DATETIME COMMENT 'consumed time',
    error_message   VARCHAR(500) COMMENT 'last consume error',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_consume_log (event_id, consumer),
    KEY idx_event_consume_log_consumer_status (consumer, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='event consume log';

CREATE TABLE notification_deliveries (
    id                  BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
    notification_id     BIGINT NOT NULL COMMENT 'notification id',
    channel             VARCHAR(32) NOT NULL COMMENT 'delivery channel',
    status              VARCHAR(32) NOT NULL COMMENT 'delivery status',
    retry_count         INT NOT NULL DEFAULT 0 COMMENT 'delivery retry count',
    last_error          VARCHAR(500) COMMENT 'last delivery error',
    sent_at             DATETIME COMMENT 'sent time',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_delivery_channel (notification_id, channel),
    KEY idx_notification_deliveries_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='notification deliveries';
