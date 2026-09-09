# S1-B first safety slice — 2026-09-09

Starting checkpoint: 4668dc1, clean after the requested S0/S1-A commit. No push.

Implemented:
- Disable implicit baselineOnMigrate. Nonempty schemas without history must not be silently treated as V1.
- Extract and test the actual Flyway configuration (no database connection).
- Main reviewer replaced the worker's unrelated PostgreSQL test URL with MySQL. The test exposed an existing missing Flyway MySQL database module: `No database found to handle jdbc:mysql`. Added flyway-mysql using the existing parent-managed version, without upgrading Flyway or Spring Boot.
- V1/V2 unchanged. No real database or Docker access.

Validation: db-migration reactor verify passed, 1 configuration test, 0 failures/errors/skips. Evidence: D:\works\semi-overt-backend\.runtime\sync-s0\s1b-baseline-guard.log.

Limitations: this is NOT the full S1-B schema classifier or data conversion. Empty/legacy/monolith/unknown classification, explicit baseline approval, field-level preflight and three-path MySQL integration tests remain open. Existing unversioned databases now require a separately reviewed onboarding path; do not manually baseline just to bypass the guard.

The earlier broad worker was stopped without changes after prolonged investigation; a narrower worker produced this safety slice and main review completed the verification repair.

Main-reviewed full reactor verify also passed: 84 tests, 0 failures/errors. Log: D:\works\semi-overt-backend\.runtime\sync-s0\s1b-full-verify.log.
