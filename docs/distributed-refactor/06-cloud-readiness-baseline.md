# 06 Cloud Readiness Baseline

## Summary
This stage keeps the Linux server runtime model fixed at `java -jar`.
It does not introduce Dockerized business services, systemd units, CI/CD, or Kubernetes manifests.

Current baseline goals:

- every service can be built with `mvn clean package`
- every service exposes `GET /actuator/health` and `GET /actuator/info`
- Linux startup is handled by `scripts/run-service.sh <service-name> [profile] [env-file]`
- runtime configuration is provided through environment variables plus Nacos
- local Windows development keeps using `scripts/dev-up.ps1` and `scripts/dev-up.cmd`

`docs/distributed-refactor/07-delivery-and-ops-baseline.md` extends this baseline with local demo orchestration, smoke checks, startup order, and troubleshooting guidance.

## Non-Goals

- no Dockerfile or container deployment artifacts for business services
- no CI/CD pipeline
- no systemd unit files
- no Kubernetes manifests
- no distributed lock for scheduled jobs

## Runtime Responsibilities

The project now has three clearly separated runtime entry layers:

1. Local Windows development
   - `scripts/dev-up.ps1`
   - `scripts/dev-up.cmd`
   - uses `spring-boot:run`
   - intended only for local development and demo联调
2. Linux server baseline
   - `scripts/run-service.sh <service-name> [profile] [env-file]`
   - runs packaged jars through `java -jar target/<service>-1.0.0.jar`
3. Later production deployment
   - intentionally deferred
   - do not treat `spring-boot:run` as a cloud runtime mode

## Linux Runtime Flow

1. Install JDK 17 or newer.
2. Ensure Nacos, MySQL, Redis, RabbitMQ, and Elasticsearch are reachable as needed by the target service.
3. Build the repo with `mvn clean package -DskipTests`.
4. Prepare runtime environment variables or an env file from `scripts/env/server.env.example`.
5. Start a service with `scripts/run-service.sh <service-name> [profile] [env-file]`.
6. Verify the service directly on its own port through `/actuator/health` and `/actuator/info`.

Example:

```bash
./scripts/run-service.sh gateway-service server ./scripts/env/server.env
```

Runtime files are written under `.runtime/`:

- `.runtime/pids/<service>.pid`
- `.runtime/logs/<service>.log`

## Configuration Baseline

Configuration precedence is fixed as:

1. environment variables
2. Nacos shared or service-specific configuration
3. module-local `application.yml` defaults

Nacos conventions:

- group: `NOW_DEMO`
- shared dataIds:
  - `shared-common.yaml`
  - `shared-db.yaml`
  - `shared-redis.yaml`
  - `shared-jwt.yaml`
- service dataIds:
  - `<service>.yaml`
  - `<service>-<profile>.yaml`

Do not rely on local-only profile files or local filesystem paths in server environments.

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
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `ELASTICSEARCH_URIS`
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
- `EVENT_OUTBOX_PUBLISH_DELAY_MS`
- `EVENT_RETRY_DELAY_MS`
- `EVENT_CONSUMER_MAX_RETRIES`
- `STORAGE_UPLOAD_PATH`
- `STORAGE_ACCESS_PREFIX`
- `STORAGE_MAX_FILE_SIZE`

## External Dependencies

- Nacos for service discovery and centralized config
- MySQL for auth, content, review, notification, and event tables
- Redis for gateway blacklist, auth reset flow, and content draft cache
- RabbitMQ for review, notification, and search event flow
- Elasticsearch for search indexing

Not every service uses every dependency directly, but the environment should provision them centrally.

## Operational Notes

- `dev-up.ps1` and `dev-up.cmd` remain local Windows development scripts only.
- `MAVEN_CMD` should point to a real `mvn.cmd` when possible.
- `MAVEN_SETTINGS` and `MAVEN_REPO_LOCAL` are the supported Maven override hooks.
- Services are considered started only when the target port is really listening and `/actuator/health` plus `/actuator/info` are reachable.
- All runtime health checks should target the service port directly, not go through the gateway.
- Logs stay on stdout/stderr and are redirected by the launcher to `.runtime/logs/`.
- Chain tracing uses `X-Trace-Id` as the single request trace header.

## Single-Instance Constraint

`content-service` still contains scheduled draft flush work.
Before introducing multiple `content-service` replicas, add distributed coordination or move the scheduled task out of the service.
For now, treat `content-service` as single-instance in server environments and demos.

## Pre-Deployment Checklist

- target ports are open and not already occupied
- Nacos server address and namespace are correct
- required Nacos config exists for the target profile
- database, Redis, RabbitMQ, and Elasticsearch connectivity is verified
- JWT signing config is consistent between gateway and auth
- file upload directory or object storage path is writable
- service jar exists under `target/` and is not a stale pre-clean artifact
- `/actuator/health` and `/actuator/info` are reachable after startup

## Later Phases

The following are intentionally deferred:

- Docker image build pipeline for services
- server-side process manager templates
- rolling deployment and rollback workflow
- multi-instance scheduling safety
- Kubernetes probes and manifests
