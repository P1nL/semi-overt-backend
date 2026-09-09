# S2-A device session persistence — 2026-09-09

Implemented the source monolith DeviceSession/RefreshTokenRotation model and JDBC repository inside auth-service. Repository operations have Spring transaction boundaries; callers composing operations must join an explicit transaction. This repository is not yet used by login or gateway authentication.

Real isolated MySQL tests cover:
- persistent=false preserved across rotation;
- old-token replay revokes only its device;
- cookie-token logout and all-user revocation;
- expiration and unknown tokens;
- two concurrent refreshes: one ROTATED, one REPLAYED, final device REVOKED;
- duplicate replacement insert rolls back consumption, allowing a valid later rotation.

Critical next-stage rule: replay/expiration statuses must be committed before translating them to HTTP 401. Throwing inside an enclosing transaction could roll back the revocation. The HTTP/service layer must return an outcome from its transaction, then map failure outside that boundary.

Tests use the two session CREATE TABLE statements from the frozen S1 V3 test resource, not a real business database. No controller, JWT issuer, gateway policy or dependency versions changed. No production access.

Remaining S2: token hashing/generation and short JWT with sid/sessionVersion; Cookie login/register/refresh/logout; gateway authoritative validation; register-code/reset-code and durable shared budgets; trusted proxy/Origin/CORS; full current-frontend session integration.

Evidence: D:\works\semi-overt-backend\.runtime\s2\session-tests.log and full-verify.log. S2 is not complete and cannot be deployed as a session protocol upgrade yet.

Full reactor verify passed with S1 and S2 MySQL tests enabled: 114 tests, 0 failures/errors/skips.
