# S1-B schema shape preflight — 2026-09-09

> semi-overt · 文档整理 2026-09-15 · 阶段资料：保留原日期、契约与验收范围；不代表当前版本、整站或生产验收。 [文档中心](../README.md)

Status: implemented and unit-tested; full schema conversion remains open.

## Behavior
- Read-only information_schema query scoped to DATABASE(); missing selected database fails before inspection.
- Recognizes EMPTY, LEGACY_MICROSERVICE, MONOLITH and UNKNOWN based on table set and critical column markers.
- Views/extra tables/missing critical columns/mixed password models fail closed. A history-only database is not treated as empty.
- Empty databases may run normal V1/V2. Unversioned nonempty databases require a separately reviewed onboarding path; no baseline override was added.
- Existing recognized legacy databases proceed to Flyway validation, then compare applied V2 with last_featured_at before migration.
- Monolith databases never run the legacy migration path, even if a history table exists.
- V1/V2 unchanged. No real database, Docker, production or external provider accessed.

## Evidence
- Full Maven reactor verify: 95 tests, 0 failures/errors/skips.
- db-migration includes 12 tests (configuration + 11 preflight cases).
- Log: D:\works\semi-overt-backend\.runtime\sync-s0\s1b-preflight-full.log.
- Test fixtures derive legacy column names from the packaged V1 SQL; JDBC inspection is mocked. This does not prove MySQL DDL migration correctness.

## Deliberate limitations
This is a shape guard, not an exact schema fingerprint. It does not yet verify every column type/width/nullability, index definition, history authenticity, row content or data compatibility. Flyway validate checks migration history, not arbitrary manual schema drift. Database names/table markers are not proof of production identity. Connections with insufficient metadata privileges and concurrent schema changes require integration validation and an operational migration lock.

Still required before S1-B completion:
1. Field/type/index/constraint comparison and source-data preflight.
2. Reviewed explicit onboarding for unversioned legacy databases, including existing V2 columns/indexes.
3. Monolith-to-target additive conversion and preservation of IDs, hashes, versions, review assignment/history.
4. Empty/legacy/monolith MySQL migration integration tests and restore rehearsal.

No broad baseline flag or data conversion is authorized merely by passing this guard.
