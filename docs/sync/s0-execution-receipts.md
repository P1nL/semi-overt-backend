# S0 execution receipts — 2026-09-09

Status: in progress. No production access, schema execution, commit or push.

## Environment
- PowerShell Core 7.6.5.
- Microsoft JDK 21.0.12.1 executing builds; Maven compiler target release 17. This is not a JDK 17 runtime validation.
- Maven wrapper 3.9.12 on target. Temporary per-invocation proxy settings under ignored .runtime/sync-s0/settings.xml; no global configuration edits.

## Baselines before implementation
| Repository | Result | Evidence |
|---|---|---|
| semi-overt-springboot @40edf51 | verify success; 143 tests, 0 failures, 0 errors, 2 skipped | D:\works\semi-overt-springboot\target\sync-s0\baseline-verify.log |
| semi-overt @a15b9fb | npm run build success | D:\works\semi-overt\node_modules\.cache\sync-s0\baseline-build.log |
| semi-overt-backend @72f71e3 | verify failed; 74 tests, 1 failure, 4 errors, 0 skipped across 28 reports | D:\works\semi-overt-backend\.runtime\sync-s0\baseline-all-modules.log |

Initial target attempts hit TLS handshake / truncated dependency transfers. Maven-specific temporary proxy settings resolved that obstruction; baseline now reaches all modules. Dependency download failures were not product test failures.

## Existing target failures
1. GatewayAuthFilterTest.whitelistedRequestWithBlacklistedTokenReturnsUnauthorized expects 401 for a public article with a revoked token; existing implementation intentionally falls back to anonymous. Repair must preserve protected resource rejection and strip untrusted identity headers, not weaken auth.
2. Four ReviewSecurityConfigTest cases fail during MockMvc creation because platform.internal.token is absent. Supply test-only configuration, never relax the production required-token guard or disable security filters.

## Direction gates
- S0: explicit existing-failure inventory plus repaired test evidence, contract fixtures and ADR review before S1.
- S1: aliases/schema preparation do not imply S2 device-session support exists.
- No cross-service database writes that bypass domain ownership.
- No dependency upgrade just to hide failing tests.
- No claim of browser/live middleware/production acceptance from unit tests.

## S0 review outcome
- Target re-run after two test-only fixes: BUILD SUCCESS, 76 tests, 0 failures/errors/skips across 28 reports.
- Log: D:\works\semi-overt-backend\.runtime\sync-s0\s0-reviewed-verify.log.
- Main reviewer caught and required correction of an intermediate mistaken assertion that would have allowed a protected revoked-token request. Final test asserts chain not called + HTTP/body 401. Production auth code was not weakened.
- Contract matrix/ADR/synthetic JSON reviewed. ADR ownership clarification required: platform-events is a library, not an independent write service; event rows belong transactionally to producers/consumers. Shared budget ownership remains a bounded S2 design decision.
- S0 baseline/contract gate passed with this clarification. S1 may proceed in bounded slices; no claim of device-session implementation or production readiness.

## S1-A review outcome
- Implemented ApiCompatibilityWebFilter before route matching; preserves raw query and method; normalizes /api to /api/v1 and approved review/upload/search/profile aliases. Existing authorization and rate limits remain downstream.
- Shared LocalDateTime serialization now includes system offset; JavaTime deserialization retained.
- Reviewer required removal of literal escape debris from generated Java and replacement of a main-only date smoke with a real JUnit/Surefire test.
- Full reactor verify passed after review: 83 tests, 0 failures, 0 errors, 0 skipped.
- Log: D:\works\semi-overt-backend\.runtime\sync-s0\s1a-reviewed-verify.log.
- This validates code/unit integration only, not live gateway routing, cookies, database upgrades or seven-service browser acceptance. Refresh/register-code/Cookie logout remain S2 work.
- S1-A complete; S1 schema migration and live routing acceptance remain open. No production modifications or Git commits.
