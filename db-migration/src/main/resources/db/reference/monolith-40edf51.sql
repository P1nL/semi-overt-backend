CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    nickname VARCHAR(60),
    email VARCHAR(120) NOT NULL UNIQUE,
    avatar_url VARCHAR(255),
    cover_url VARCHAR(512),
    signature VARCHAR(50),
    password_hash VARCHAR(120) NOT NULL,
    session_version BIGINT NOT NULL DEFAULT 0,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rate_limit_buckets (
    bucket_key VARCHAR(160) PRIMARY KEY,
    window_started_at BIGINT NOT NULL,
    request_count BIGINT NOT NULL,
    expires_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS auth_device_sessions (
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

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    family_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    replaced_by_hash VARCHAR(64) NULL
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    code_salt VARCHAR(64) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS email_verification_codes (
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

CREATE TABLE IF NOT EXISTS articles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    title VARCHAR(120),
    content TEXT,
    summary VARCHAR(255),
    cover_url VARCHAR(512),
    cover_color VARCHAR(32),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    word_count INT NOT NULL DEFAULT 0,
    read_minutes DECIMAL(6, 1) NOT NULL DEFAULT 0.0,
    duration_category VARCHAR(20) NOT NULL DEFAULT 'QUICK',
    submit_count INT NOT NULL DEFAULT 0,
    last_submitted_at TIMESTAMP NULL,
    published_at TIMESTAMP NULL,
    draft_visible BOOLEAN NOT NULL DEFAULT FALSE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS home_article_exposures (
    user_id BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    first_exposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_exposed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exposure_count INT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, article_id)
);

CREATE TABLE IF NOT EXISTS review_tasks (
    article_id BIGINT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    assigned_admin_id BIGINT NOT NULL,
    title VARCHAR(120),
    submit_count INT NOT NULL DEFAULT 0,
    word_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS review_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
