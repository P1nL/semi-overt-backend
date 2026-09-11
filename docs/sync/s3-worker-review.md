# S3 worker direction review

Workers used the user-requested `gpt-5.6-sol` / `xhigh`; main agent owns integration and actual evidence. This ledger records actual findings and corrections, not blind acceptance of worker reports.

## Frozen checks
1. No review/notification writes to articles or auth users. No remote call inside the write-claim transaction.
2. Content DB/CAS owns state; Redis never flushes unversioned drafts to DB.
3. Same decision key binds payload and generation; only authority-confirmed FINAL is successful.
4. Version tombstones reject older cancellation/submission; late results never close a newer task.
5. Publish only after broker confirm with mandatory returns; lease-token fencing protects ownership.
6. Inbox deduplication and business writes share one local transaction; ack happens after commit.
7. Concurrency/failure tests use actual isolated MySQL/Rabbit when that is the claim. Mock tests remain explicitly separate.

## Directions reviewed so far
- Content: main agent corrected initial schema suggestion: `content_review_decisions` must NOT have unique(article_id,submission_id), because a CONFLICT command must not reserve a valid final-result slot. Article lock/CAS owns final uniqueness. Required no remote write call in content transaction, <=64-char event UUIDs, exact assigned-reviewer access.
- Review: required claim/outbox commit BEFORE content call and separate finalization transaction; PROCESSING must be recovered and queried, not returned as HTTP 200. Required historical log handling independent of newer task closure, monotonic tombstones, admin-owned candidate endpoint.
- Events: worker direction explicitly reviewed and approved: PENDING+lease fields, producer/event-type scope, correlation confirms+returns, transaction-bound inbox. Added review requirements for expired-lease late confirmation fencing and separating ack IO failures from business rollback/retry (do not overwrite committed SUCCESS).
- Frontend: reviewed first actual version propagation patches (DTO -> VM -> store/payload -> receipt); narrow scope/no visual rewrite. Required safe nonnegative integer versions and removal of browser-now fallback for server save/review timestamps. Unknown retries must preserve decisionId across explicit retry/refresh.

## Main integration discoveries
- Existing schema parser's alternation could swallow V4 ALTER statements between CREATE TABLE statements; replaced with a bounded optional ENGINE suffix. Existing source/reference and MySQL migration tests passed after correction.
- S3 permits eventual projection lag and terminal tombstones; updated post-V4 data checks without loosening approved pre-V4 onboarding checks.
- Notification parallel distinct-event replay exposed InnoDB lock-upgrade deadlock when duplicate inserts were followed by `FOR UPDATE`. Changed duplicate-key verification to a current `FOR SHARE` read; real concurrent production-mapper/service test now passes, preserving one notification/two deliveries.
- Added gateway decision-status alias and auth-owned admin candidate endpoint; these were missing integration edges, not delegated module changes.

## Existing unrelated work protected
S5 compose/launcher/docs and frontend Vite proxy/`scripts/dev-frontend.*` are preexisting local changes. No reset/clean or broad staging. S5 middleware had been stopped externally and was restarted; no old business services were started against the changing schema.

Pending: inspect completed worker implementations, targeted corrective feedback, full integration build, real cross-service acceptance, final evidence and boundaries.

## Actual patch findings and corrections requested
- ReviewTaskServiceImpl initially used REQUIRES_NEW, committed projection outside Inbox, and mutated `target=current` before detecting a new submission. Requested REQUIRED and capturing old submission/status/version before mutation. Also rejected same-version different-state updates. Worker patch now reflects these corrections; runtime tests still pending.
- ReviewDecisionCoordinatorImpl initially saved aggregateType `review-command`, while its publisher only claimed `review`; requested the frozen `review` namespace. Also required REQUIRED finalization, fixed task->command lock order, action/status and expectedVersion+1 checks, authoritative timestamp, and rejecting oversized keys rather than silently hashing them.
- Outbox implementation had JVM-time lease checks and a 20-record lease batch whose worst-case confirm time exceeded lease duration. Requested DB time and one-at-a-time claims; worker acknowledged the corrections.
- Inbox implementation initially retained deprecated separately committed status writes and lacked a guard for an outer transaction delaying actual commit past ack. Requested fail-closed legacy APIs, <=64/128 identity bounds, actual-commit protection, and duplicate-lock tests.
- Frontend UNKNOWN implementation initially cleared keys on FINAL, lost memory fallback after sessionStorage failure, lacked actor identity, and normalized query HTTP status to 200. Requested retained stable keys, storage fallback merging, actor/generation fingerprint, and actual 200+matching decisionId for query finalization. Narrow UI prop plumbing reviewed as in-scope.
- Added authoritative pending-snapshot keyset API to repair PENDING articles whose tasks are absent; avoids review directly querying content's tables.

These findings are not assumed fixed merely because sent to workers. Final code and runtime evidence must recheck them.

## Integrated evidence and dispositions
- Main agent re-read corrected projection/coordinator/Outbox/Inbox/authority/ frontend implementations. The listed REQUIRED, mutation order, NULL clearing, fencing, timestamp, payload binding, and outer-transaction checks are present in the final code.
- Real seven-service acceptance: 71 checks passed in `.runtime/s3/accept-20260910-172940/receipt.json`. This included opposing decisions, same-key replay, old page generation rejection, late cancel delivery, notification uniqueness, missing-task repair, and Content process outage/restart recovery.
- Full reactor isolation build: 171 tests passed with zero failures/errors/skips at 17:50:42; final rerun follows remaining source cleanup. Workers' mock-only nack/timeout tests are not represented as real broker failure injection. Actual Rabbit tests independently cover mandatory return, successful confirm, retry connection failure, physical ack-loss redelivery, invalid payload DLQ and replay.
- Additional main-agent integration fix: shared Jackson emitted offset timestamps but could not deserialize its own LocalDateTime wire payloads. Added bidirectional ISO offset/local parsing and roundtrip tests; actual Rabbit and Feign paths subsequently passed.
- IDE/JDT wrote `Unresolved compilation problem` class stubs into shared target during parallel work. Added opt-in `s3.buildRoot` Maven output isolation and immutable SHA256-checked per-run jar copies; no IDE settings or parallel work were reset.
- Real frontend editor/review flows verified with isolated users: save -> submit -> assigned reviewer -> FINAL plus DB/log/notification receipt. Browser review POST carried decisionId/submissionId/expectedVersion; no UI-only success inference.
- Browser additionally exposed the frontend's old inferred 30-minute cooldown and missing submit/cancel version propagation. Worker corrected only related logic; cancel -> edit/save -> immediate resubmit was retested successfully. Existing unrelated `[object Promise]` background rendering was recorded, not silently folded into S3.
- No changes were committed, staged, pushed or deployed; S5 environment setup and unrelated frontend work remain distinct.
- Final main-agent verification: backend 171/0/0/0 at 2026-09-10 18:05:26; frontend S3 15/15, auth-session 14/14 and production build passed. Final test review corrected an ineffective dirty-state regex before re-running S3 frontend tests.
