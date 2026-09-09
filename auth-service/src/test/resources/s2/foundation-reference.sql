-- S1 legacy route: migrate the existing V1 + V2 microservice schema in place.
-- Flyway executes this version once for the selected db/migration location.
-- This script is intentionally non-idempotent and does not rewrite historical rows.

ALTER TABLE users
    MODIFY COLUMN username VARCHAR(32) NOT NULL,
    MODIFY COLUMN nickname VARCHAR(60) NULL,
    MODIFY COLUMN email VARCHAR(120) NOT NULL,
    MODIFY COLUMN password VARCHAR(120) NOT NULL,
    MODIFY COLUMN cover_url VARCHAR(512) NULL,
    MODIFY COLUMN signature VARCHAR(100) NULL,
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE articles
    MODIFY COLUMN cover_url VARCHAR(512) NULL,
    MODIFY COLUMN cover_color VARCHAR(32) NULL,
    MODIFY COLUMN read_minutes DECIMAL(6, 1) NOT NULL DEFAULT 0.0,
    ADD COLUMN draft_visible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE review_tasks
    ADD COLUMN assigned_admin_id BIGINT NULL;

ALTER TABLE review_logs
    MODIFY COLUMN from_status ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NULL,
    MODIFY COLUMN to_status ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NULL;

ALTER TABLE notifications
    MODIFY COLUMN biz_id BIGINT NULL;

CREATE TABLE rate_limit_buckets (
    bucket_key VARCHAR(160) PRIMARY KEY,
    window_started_at BIGINT NOT NULL,
    request_count BIGINT NOT NULL,
    expires_at BIGINT NOT NULL
);

CREATE TABLE auth_device_sessions (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    family_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP NOT NULL,
    idle_expires_at TIMESTAMP NOT NULL,
    absolute_expires_at TIMESTAMP NOT NULL,
    persistent BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMP NULL
);

CREATE TABLE auth_refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    family_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    replaced_by_hash VARCHAR(64) NULL
);

CREATE TABLE password_reset_tokens (
    token VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_salt VARCHAR(64) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE email_verification_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(120) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    code_salt VARCHAR(64) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE home_article_exposures (
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    first_exposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_exposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exposure_count INT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, article_id)
);
