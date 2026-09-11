-- S3 additive migration. Run only with all writers stopped; never modify V1-V3.
ALTER TABLE articles ADD COLUMN submission_id VARCHAR(64) NULL;
UPDATE articles SET submission_id=CONCAT('legacy-',id,'-',submit_count)
 WHERE status<>'DRAFT' OR submit_count>0;

CREATE TABLE IF NOT EXISTS content_author_locks (
    author_id BIGINT NOT NULL,
    PRIMARY KEY (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS content_review_decisions (
    decision_id VARCHAR(64) NOT NULL,
    article_id BIGINT NOT NULL,
    submission_id VARCHAR(64) NOT NULL,
    expected_version BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason TEXT NULL,
    state VARCHAR(16) NOT NULL,
    status VARCHAR(16) NULL,
    article_version BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (decision_id),
    KEY idx_content_decision_submission (article_id,submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE review_tasks
    ADD COLUMN submission_id VARCHAR(64) NULL,
    ADD COLUMN last_applied_version BIGINT NOT NULL DEFAULT -1,
    ADD COLUMN decision_id VARCHAR(64) NULL,
    ADD COLUMN command_state VARCHAR(16) NOT NULL DEFAULT 'OPEN';
UPDATE review_tasks t JOIN articles a ON a.id=t.article_id
 SET t.submission_id=a.submission_id,t.last_applied_version=a.version;
-- Historical unassigned tasks: deterministic non-author admin; no admin stays unassigned.
UPDATE review_tasks t SET assigned_admin_id=(
 SELECT MIN(u.id) FROM users u WHERE u.role='ADMIN' AND u.id<>t.author_id
) WHERE t.assigned_admin_id IS NULL;
CREATE INDEX idx_review_assignment ON review_tasks (assigned_admin_id,status,submitted_at);

CREATE TABLE IF NOT EXISTS review_commands (
    decision_id VARCHAR(64) NOT NULL,
    article_id BIGINT NOT NULL,
    submission_id VARCHAR(64) NOT NULL,
    expected_version BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason TEXT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    status VARCHAR(16) NULL,
    article_version BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    error_message VARCHAR(512) NULL,
    PRIMARY KEY (decision_id),
    UNIQUE KEY uk_review_command_submission (article_id,submission_id),
    KEY idx_review_command_state_updated (state,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE review_logs
    ADD COLUMN decision_id VARCHAR(64) NULL,
    ADD COLUMN submission_id VARCHAR(64) NULL,
    ADD COLUMN article_version BIGINT NULL,
    ADD UNIQUE KEY uk_review_log_decision (decision_id);
ALTER TABLE event_outbox
    ADD COLUMN lease_owner VARCHAR(128) NULL,
    ADD COLUMN lease_token VARCHAR(64) NULL,
    ADD COLUMN lease_until DATETIME(6) NULL,
    ADD INDEX idx_event_outbox_lease (status,lease_until,next_retry_at);
ALTER TABLE notifications
    ADD COLUMN decision_id VARCHAR(64) NULL,
    ADD UNIQUE KEY uk_notification_decision (decision_id);
