# S3 implementation contract — 2026-09-10

This is the implementation decision for ADR-003/004/005, not an acceptance claim.

## Ownership and transactions
- content owns articles/content_author_locks/content_review_decisions. review owns review_tasks/review_commands/review_logs. auth exposes admin candidates; nobody writes users outside auth.
- review claim+command+command outbox is one local transaction, committed BEFORE calling content. Content locks/CASes article, binds decision payload, persists FINAL/CONFLICT and accepted status outbox in one local transaction. review finalization is a separate transaction and only FINAL yields the successful review log.
- Rabbit command replay and synchronous application use exactly the same decisionId. Unknown is never success. POST waits only for one bounded Feign call (connect 2s/read 5s), otherwise HTTP 503 with retained decisionId; query returns PROCESSING/FINAL/CONFLICT. No HTTP 202-as-success.
- Commands/results are retained indefinitely in S3 (no cleanup scheduler). Frontend queries at most 4 times, 500/1000/2000/4000ms, then keeps UNKNOWN and the same key for an explicit retry. No automatic new decision key after uncertain outcomes.
- Public decision request carries optional submissionId/expectedVersion. New frontend sends the loaded article baseline; review checks it under the task claim lock. An old S1 page must not silently claim S2. Legacy omitted baseline remains a compatibility mode, not protection against stale pages. Same-key replay validates against the retained command baseline rather than the current task generation.
- Projection keeps a per-article version tombstone, including cancel/delete. Same/older articleVersion cannot reopen or delete a newer task. Late confirmed decision may produce its unique historical log but cannot close a newer submission.

## Shared Java contract
- `ApplyReviewResultReq`: existing adminId/action/reason plus String decisionId/submissionId, Long expectedVersion. Keep the old 3-arg constructor for source compatibility, but runtime rejects unversioned writes.
- `ReviewDecisionResultDto`: decisionId, articleId, submissionId, state (FINAL/CONFLICT), status (ArticleStatus), version, updatedAt (LocalDateTime), adminId, action (ReviewAction), reason.
- ContentReviewClient `applyReviewResult(id,req)` -> Result<ReviewDecisionResultDto>; `reviewDecisionResult(id,decisionId)` -> same, GET `/internal/articles/{id}/review-decisions/{decisionId}`. A missing result is 404, never guessed success.
- ArticleReviewSnapshotDto adds submissionId/version/deleted/updatedAt.
- ContentReviewClient `pendingReviewSnapshots(afterId,limit)` returns Result<List<ArticleReviewSnapshotDto>>, GET `/internal/articles/review-pending`, ascending id keyset page, max 100. Review reconciliation maintains a rotating cursor and uses this authoritative read API to discover PENDING articles missing tasks; no direct review JDBC reads/writes to articles.
- AuthUserQueryClient `listReviewAdmins()` -> Result<List<UserSummaryDto>>, GET `/internal/users/review-admins`. UserSummaryDto unchanged: endpoint guarantees eligible ADMIN candidates, sorted id, excludes deleted.
- ReviewTaskClient `assignment(articleId,submissionId)` -> Result<ReviewAssignmentDto>, GET `/internal/review/tasks/{articleId}/assignment?submissionId=...`; DTO articleId/submissionId/assignedAdminId. Exact submission required. No candidates means unassigned/pending, not arbitrary self-assignment.
- ReviewTaskUpsertReq/ReviewTaskRemoveReq add submissionId/articleVersion; unversioned projection writes fail closed.

## Events
- ArticleSubmittedEvent adds submissionId, articleVersion, title, wordCount.
- ArticleStatusChangedEvent adds submissionId, articleVersion, deleted, decisionId, adminId, action, reason.
- ReviewDecidedEvent is the legacy-named durable COMMAND, not proof of a final decision. It and ReviewDecisionPayload add decisionId/submissionId/expectedVersion.
- Event ID is a UUID (<=64 characters); random IDs deduplicate but never order.
- Only content accepted status-change events trigger final review and notifications. Rejected commands can be recovered by querying their durable result; scheduled review reconciliation must recover PROCESSING commands.

## V4 schema (both approved source migration routes)
- Protocol cutover is offline: drain/reconcile old Outbox, unresolved Inbox attempts and all old broker queues before starting S3 consumers. Migration rejects PENDING/DEAD old Outbox or non-SUCCESS old Inbox before writing; a PUBLISHED row alone cannot prove the broker queue was drained. No fabricated version backfill for old messages and no automatic replay of legacy review decisions.
- articles.submission_id VARCHAR(64) NULL; legacy submitted rows deterministically backfilled from article id + submit_count without changing version.
- content_author_locks(author_id BIGINT PK).
- content_review_decisions(decision_id VARCHAR64 PK, article_id BIGINT, submission_id VARCHAR64, expected_version BIGINT, admin_id BIGINT, action VARCHAR16, reason TEXT NULL, state VARCHAR16, status VARCHAR16 NULL, article_version BIGINT NULL, updated_at DATETIME6). Only decision_id is unique; a conflict must not reserve the successful submission slot.
- review_tasks adds submission_id VARCHAR64 NULL, last_applied_version BIGINT NOT NULL DEFAULT -1, decision_id VARCHAR64 NULL, command_state VARCHAR16 NOT NULL DEFAULT 'OPEN'. assigned_admin_id already exists.
- review_commands(decision_id VARCHAR64 PK,article_id BIGINT,submission_id VARCHAR64,expected_version BIGINT,admin_id BIGINT,action VARCHAR16,reason TEXT NULL,payload_hash VARCHAR64,state VARCHAR16,status VARCHAR16 NULL,article_version BIGINT NULL,created_at DATETIME6,updated_at DATETIME6,error_message VARCHAR512 NULL), UNIQUE(article_id,submission_id).
- review_logs adds decision_id VARCHAR64 NULL UNIQUE,submission_id VARCHAR64 NULL,article_version BIGINT NULL. Legacy rows preserve NULL.
- event_outbox adds lease_owner VARCHAR128 NULL,lease_token VARCHAR64 NULL,lease_until DATETIME6 NULL. Keep PENDING with lease fields unless the event worker explicitly requests a status expansion.
- notifications adds decision_id VARCHAR64 NULL UNIQUE for accepted-decision delivery idempotency (legacy NULL preserved).

## Review gates
Main agent reviews workers' direction and actual patches: ownership, tx boundaries, CAS conditions, stale event handling, key-payload binding, unknown responses, publisher confirms, and tests. No production access. All real tests use isolated S5 middleware and new randomized s3_* schemas/queues. Existing S5 env additions/frontend launcher dirt remain separate and are not silently staged or committed.
