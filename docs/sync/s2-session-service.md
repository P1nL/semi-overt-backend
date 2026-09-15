# S2-B service and short access token — 2026-09-09

> semi-overt · 文档整理 2026-09-15 · 阶段资料：保留原日期、契约与验收范围；不代表当前版本、整站或生产验收。 [文档中心](../README.md)

Adds DeviceSessionService and SessionAccessTokenIssuer. Not connected to legacy AuthController or gateway yet.

- Cryptographic 32-byte base64url refresh credentials; only SHA-256 hashes persist.
- Access JWT has sid/sessionVersion and at most 900-second TTL.
- Refresh idle/absolute defaults mirror source (30/90 days), capped and validated.
- Session service uses a REQUIRES_NEW durable transaction and returns status after commit. HTTP mapping remains outside the transaction so replay revocation survives an outer exception.
- Opening a session accepts only an already-authenticated server-side user ID. Registration/password validation must commit before calling it; this is not a public authentication shortcut.
- Real isolated MySQL tests assert stored hash, sid/version/TTL, persistence across refresh, replay revocation surviving an outer rollback, logout, malformed tokens and missing user.

Important: new service availability is not protocol cutover. Current login/register and GatewayAuthFilter are still legacy. Pending work includes cookie endpoints, atomic authentication-to-session coordination, authoritative gateway session checks, reset/register code and durable budgets. No production deployment.

Logs: D:\works\semi-overt-backend\.runtime\s2\service-test.log and service-full-verify.log.

Full reactor verify with both MySQL suites enabled: 116 tests, 0 failures/errors/skips.
