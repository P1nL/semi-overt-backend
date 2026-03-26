# 06 Cloud Readiness Baseline

## Summary
This stage prepares the distributed services for Linux server deployment with `java -jar`.
It does not introduce Docker, CI/CD, systemd, or Kubernetes manifests.

Current baseline goals:

- every service can be built with `mvn clean package`
- every service exposes `GET /actuator/health` and `GET /actuator/info`
- Linux startup is handled by `scripts/run-service.sh`
- runtime configuration is provided through environment variables and Nacos

## Non-Goals

- no Dockerfile or container deployment artifacts
- no CI/CD pipeline
- no systemd unit files
- no Kubernetes manifests
- no distributed lock for scheduled jobs

## Linux Runtime Flow
1. Install JDK 17 or newer.
2. Make sure Nacos, MySQL, Redis, RabbitMQ, and Elasticsearch are reachable as needed by the target service.
3. Build the repo with `mvn clean package -DskipTests`.
4. Prepare runtime environment variables or an env file from `scripts/env/server.env.example`.
5. Start a service with `scripts/run-service.sh <service-name> [profile] [env-file]`.

Example:

```bash
./scripts/run-service.sh gateway-service server ./scripts/env/server.env
```

Runtime files are written under `.runtime/`:

- `.runtime/pids/<service>.pid`
- `.runtime/logs/<service>.log`

## Environment Variables
Common runtime variables:

- `SPRING_PROFILES_ACTIVE`
- `SERVER_PORT`
- `NACOS_SERVER_ADDR`
- `NACOS_NAMESPACE`

External dependency variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_DB`
- `JWT_SIGN_KEY`
- `JWT_EXPIRATION`
- `JWT_REMEMBER_ME_EXPIRATION`
- `JWT_REFRESH_THRESHOLD`
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `FRONTEND_BASE_URL`
- `RESET_PWD_TOKEN_TTL_MINUTES`
- `SUBMIT_REVIEW_COOLDOWN_MINUTES`
- `DRAFT_CACHE_TTL_DAYS`
- `DRAFT_FLUSH_INTERVAL_MINUTES`
- `STORAGE_UPLOAD_PATH`
- `STORAGE_ACCESS_PREFIX`
- `STORAGE_MAX_FILE_SIZE`

Use environment variables for direct overrides.
Use Nacos for shared configuration that should stay centralized.
Do not rely on local-only files or local filesystem paths in cloud environments.

## External Dependencies

- Nacos for service discovery and config
- MySQL for auth/content/review persistence
- Redis for gateway blacklist, auth reset flow, and content draft cache
- RabbitMQ for notification-related messaging
- Elasticsearch for search-related evolution

Not every service uses every dependency directly, but the environment should provision them centrally.

## Operational Notes

- `dev-up.ps1` and `dev-up.cmd` remain local Windows development scripts only.
- Production-like Linux runs should use packaged jars instead of `spring-boot:run`.
- Logs stay on stdout/stderr and are redirected by the launcher to `.runtime/logs/`.
- All services expose `/actuator/health` and `/actuator/info` on the application port.

## Single-Instance Constraint

`content-service` currently enables scheduling and runs draft flush tasks.
Before introducing multiple `content-service` replicas, add distributed coordination or move the scheduled task out of the service.
For now, plan for a single `content-service` instance in server environments.

## Pre-Deployment Checklist

- target ports are open and not already occupied
- Nacos server and namespace are correct
- required Nacos config exists for the target profile
- database and Redis connectivity is verified
- JWT signing config is consistent between gateway and auth
- file upload directory or object storage path is writable
- service jar exists under `target/` and is not a stale pre-clean artifact

## Later Phases

The following are intentionally deferred:

- Docker image build pipeline
- server-side process manager templates
- rolling deployment and rollback workflow
- multi-instance scheduling safety
- Kubernetes probes and manifests
