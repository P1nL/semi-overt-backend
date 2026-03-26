# 07 Delivery And Ops Baseline

## Summary

This document extends the cloud readiness baseline with a local demo and integration workflow.
It keeps the runtime model lightweight:

- infrastructure is started through `docker-compose.yml`
- local business services are started through `scripts/dev-up.ps1`
- Linux baseline still uses `scripts/run-service.sh`
- observability stays at the level of health endpoints, logs, queue visibility, and a smoke script

The goal is to let a new teammate clone the repo, start the stack quickly, and demo the full chain:

`register/login -> submit for review -> approve -> notification stored -> searchable`

## Runtime Layers

The three runtime layers are fixed:

1. Local Windows development
   - entry: `scripts/dev-up.ps1` or `scripts/dev-up.cmd`
   - purpose: local development, demo rehearsal, multi-service联调
2. Linux server baseline
   - entry: `scripts/run-service.sh <service-name> [profile] [env-file]`
   - purpose: packaged jar startup through `java -jar`
3. Later formal deployment
   - intentionally not implemented in this stage

Do not mix these responsibilities.

## Local Infrastructure Plan

`docker-compose.yml` is the single local infrastructure orchestration file.
It starts only middleware, not business services.

Default services:

- MySQL `8.0.36`
  - host port: `3306`
  - schema init: `src/main/resources/init.sql`
  - default local root password: `1234`
- Redis `7.2`
  - host port: `6379`
- Nacos `2.3.2`
  - host ports: `8848`, `9848`
  - mode: standalone
- RabbitMQ `3-management`
  - host ports: `5672`, `15672`
- Elasticsearch `8.13.4`
  - host port: `9200`
  - single node
  - security disabled

Optional local tooling:

- Kibana is available only under compose profile `ops-ui`

Every infrastructure container has a health check.
`scripts/dev-up.ps1` waits for all of them before starting business services.

## Service Ports And Config Rules

Service ports are fixed:

- `gateway-service`: `8080`
- `auth-service`: `8081`
- `content-service`: `8082`
- `review-service`: `8083`
- `search-service`: `8084`
- `file-service`: `8085`
- `notification-service`: `8086`

Configuration precedence is fixed:

1. environment variables
2. Nacos
3. module-local defaults in `application.yml`

Nacos conventions are fixed:

- group: `NOW_DEMO`
- shared dataIds:
  - `shared-common.yaml`
  - `shared-db.yaml`
  - `shared-redis.yaml`
  - `shared-jwt.yaml`
- service dataIds:
  - `<service>.yaml`
  - `<service>-<profile>.yaml`

The local development baseline may continue to use the validated namespace id already baked into `dev-up.ps1`, but documentation and server examples must still treat `NACOS_NAMESPACE` as an explicit runtime input.

## Local Startup Order

The local startup order is fixed:

1. `docker compose up -d`
2. wait for middleware health
3. install parent POM and `common`
4. start services in this order:
   - `auth-service`
   - `content-service`
   - `review-service`
   - `search-service`
   - `notification-service`
   - `file-service`
   - `gateway-service`
5. for every service, confirm:
   - target port is actually listening
   - `GET /actuator/health` returns `UP`
   - `GET /actuator/info` is reachable

## Windows Local Launcher Rules

`scripts/dev-up.ps1` is the canonical local launcher.

Fixed launcher rules:

- `MAVEN_CMD` is the first-class Maven entry and should point to a real `mvn.cmd`
- `dev-up.ps1` supports:
  - `-StartupMode stable|fast`
  - `-ForceRebuildCommon`
  - `-RestartServices`
- default mode is still `stable`
- `fast` mode is intended for repeated local startup when middleware and healthy services already exist
- recommended example path on this machine:

```text
C:\Users\PINKING\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd
```

- PowerShell calls Maven directly through `& $MAVEN_CMD @args`
- do not wrap Maven inside `cmd.exe /d /c call ...`
- Maven overrides must go through:
  - `MAVEN_SETTINGS`
  - `MAVEN_REPO_LOCAL`
- arguments such as `-Dspring-boot.run.profiles=local` and `-Dmaven.repo.local=...` must stay intact as single arguments
- a service is only considered started when the port is really listening
- stale launcher PIDs are not enough to treat a service as running
- rerunning `dev-up.ps1` after a failed boot must not be blocked by stale launcher PIDs

`scripts/dev-up.cmd` is only a thin wrapper around the PowerShell script.

## Linux Runtime Notes

Linux runtime rules stay aligned with the cloud readiness baseline:

- entry: `scripts/run-service.sh <service-name> [profile] [env-file]`
- runtime form: `java -jar target/<service>-1.0.0.jar`
- verify each service directly on its own port
- do not treat `spring-boot:run` as a server runtime form

Current runtime restriction:

- `content-service` must remain single-instance because draft flush scheduling has not been made distributed-safe yet

## TraceId And Logging Rules

The unified request trace header is:

- `X-Trace-Id`

Propagation rules:

1. `gateway-service` accepts incoming `X-Trace-Id`
2. if missing, `gateway-service` generates a UUID
3. gateway injects `X-Trace-Id` into downstream HTTP requests
4. Feign clients relay the same value
5. MQ event payloads carry the same `traceId`

Logging rules for this stage:

- do not introduce a heavy tracing platform
- rely on service logs and event payloads
- key business logs should print:
  - `traceId`
  - `serviceName`
  - and when applicable: `userId`, `articleId`, `eventId`

Recommended manual trace points:

- article submitted
- review decided
- article status changed
- notification consumer handled event
- search consumer indexed article

## Health Checks And Minimal Monitoring

Service health checks are fixed:

- `GET /actuator/health`
- `GET /actuator/info`

Direct service endpoint checks:

- `http://127.0.0.1:8080/actuator/health`
- `http://127.0.0.1:8081/actuator/health`
- `http://127.0.0.1:8082/actuator/health`
- `http://127.0.0.1:8083/actuator/health`
- `http://127.0.0.1:8084/actuator/health`
- `http://127.0.0.1:8085/actuator/health`
- `http://127.0.0.1:8086/actuator/health`

Infrastructure checks:

- MySQL:
  - port `3306`
  - schema `content_platform` exists
- Redis:
  - `redis-cli ping`
- Nacos:
  - `http://127.0.0.1:8848/nacos/actuator/health`
  - console: `http://127.0.0.1:8848/nacos`
- RabbitMQ:
  - AMQP `5672`
  - management console: `http://127.0.0.1:15672`
- Elasticsearch:
  - `http://127.0.0.1:9200/_cluster/health`

Minimal monitoring items for this stage:

- all 7 services expose healthy actuator endpoints
- Nacos shows all expected registered instances
- RabbitMQ main queues, retry queues, and DLQs are visible
- `event_outbox` has no abnormal `DEAD` growth
- `event_consume_log` has no abnormal `FAILED` growth
- `notification_deliveries` failure count remains explainable
- search delay after review approval stays within demo-acceptable latency
- gateway logs do not show abnormal bursts of `401`, `403`, or `5xx`

## Smoke And Acceptance Scripts

The lightweight validation entry is:

- `scripts/smoke-test.ps1`

What it verifies:

- Docker middleware is healthy
- all 7 services answer `/actuator/health` and `/actuator/info`
- gateway public route `/api/v1/home` is reachable anonymously
- invalid token on a protected route returns `401`
- end-to-end flow works:
  - register author
  - register reviewer candidate
  - promote reviewer candidate to `ADMIN` in local MySQL
  - author login
  - reviewer login
  - create article
  - save draft
  - submit for review
  - approve review
  - wait for notification rows
  - wait for search visibility
  - confirm the same `X-Trace-Id` appears in local service logs

Quick usage:

```powershell
./scripts/smoke-test.ps1
```

Health-only usage:

```powershell
./scripts/smoke-test.ps1 -SkipE2E
```

## Demo Flow

Recommended 8-minute interview demo:

1. Start infrastructure:

```powershell
docker compose up -d
```

2. Set an explicit Maven command:

```powershell
$env:MAVEN_CMD = 'C:\Users\PINKING\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd'
```

3. Start local services:

```powershell
./scripts/dev-up.ps1
```

Fast repeat-start example:

```powershell
./scripts/dev-up.ps1 -StartupMode fast
```

4. Show actuator checks and service registration in Nacos.
5. Run the smoke script or replay its steps manually through the gateway.
6. Show notification rows and search results after review approval.
7. Explain:
   - local infra is compose-managed
   - business services stay as plain Spring Boot processes
   - `content-service` is still single-instance
   - observability is intentionally lightweight but enough for a demo project

## Troubleshooting Notes

These checks must stay in the runbook:

- if the home page returns `500`, first confirm `gateway-service` actually started successfully
- if the gateway fails to start, first inspect whether a `DataSource` dependency was accidentally pulled in or a non-conditional bean from `common` dragged it down
- if an event chain appears idle, inspect `event_outbox.status` and the scheduled publish window before blaming RabbitMQ or the consumer
- if a local relaunch says a service is already running, verify the actual listening port instead of trusting a stale PID file

## Regression Focus

The delivery and ops regression focus must explicitly cover:

- single-module startup no longer hits `Unknown lifecycle phase ".run.profiles=local"`
- rerunning the local launcher after a failed boot is not blocked by stale launcher PIDs
- after gateway restart:
  - public routes still allow anonymous access
  - valid tokens still propagate user identity
  - invalid tokens still return `401`
- Linux `run-service.sh` startup leaves `/actuator/health` and `/actuator/info` reachable
- the runbook keeps local scripts, Linux baseline, and later formal deployment clearly separated
