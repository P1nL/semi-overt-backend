# now-demo

`now-demo` 是一个围绕“内容创作 -> 提交审核 -> 审核决定 -> 通知投递 -> 搜索可见”设计的 Spring Boot 多模块微服务示例仓库。

当前运行主实现是父 [pom.xml](./pom.xml) 管理的 Maven 多模块工程，公网流量统一经 `gateway-service` 进入，再分发到认证、内容、审核、搜索、文件和通知服务。

## 这份仓库适合谁

- 后端开发：接手系统、改需求、联调、排障、发布
- 前端 / 测试：快速定位接口入口、鉴权规则、联调路径、冒烟方式
- 运维 / 平台：确认运行依赖、端口、配置来源、发布入口和 Nginx 基线

## 系统快照

业务服务：

- `gateway-service`：公网入口、JWT 校验、内部头注入、限流、路由
- `auth-service`：注册、登录、找回密码、用户资料、内部用户查询
- `content-service`：首页、分类、文章、草稿、提审、详情、内部内容接口
- `review-service`：审核待办、审核动作、审核日志、审核投影
- `search-service`：公开搜索与搜索事件消费
- `file-service`：图片上传与静态文件访问
- `notification-service`：通知事件消费、通知与投递记录

支撑模块：

- `platform-kernel`：共享常量、基础类型、公共结果模型
- `platform-web-support`：Web / Feign / Security 公共支持
- `platform-events`：Outbox、RabbitMQ 拓扑、事件发布与消费基础设施
- `auth-contract` / `content-contract` / `review-contract`：内部契约与 Feign client
- `architecture-tests`：架构边界约束测试

## 快速开始

### 1. 启动中间件

```powershell
docker compose up -d
```

默认启动：

- MySQL `3306`
- Redis `6379`
- Nacos `8848`
- Nacos gRPC `9848`
- RabbitMQ `5672`
- RabbitMQ 管理台 `15672`

### 2. 启动本地服务

```powershell
.\scripts\dev-up.ps1
```

默认启动顺序：

1. `auth-service`
2. `content-service`
3. `review-service`
4. `search-service`
5. `notification-service`
6. `file-service`
7. `gateway-service`

运行日志写入 `.codex-runtime/logs`，PID 写入 `.codex-runtime/pids`。
排查最近错误可直接运行 `.\scripts\dev-logs.ps1`；要看原始日志尾部可运行 `.\scripts\dev-logs.ps1 -All`。

### 3. 跑通冒烟

```powershell
.\scripts\smoke-test.ps1
```

脚本会校验：

- Docker 中间件健康状态
- 所有服务的 `/actuator/health` 与 `/actuator/info`
- 网关公开路由与无效 token 的 `401`
- 注册 -> 草稿 -> 提审 -> 审核通过 -> 通知入库 -> 搜索可见 的主链路

## 文档导航

### 起步

- [起步索引](./docs/01-start-here/README.md)
- [项目概览](./docs/01-start-here/01-project-overview.md)
- [角色化阅读路径](./docs/01-start-here/02-reading-paths.md)
- [首次接手与改需求入口](./docs/01-start-here/03-first-day-handoff.md)
- [10 分钟跑通本地环境](./docs/01-start-here/04-ten-minute-run.md)

### 架构

- [架构索引](./docs/02-architecture/README.md)
- [模块边界](./docs/02-architecture/01-module-boundaries.md)
- [核心链路与状态流转](./docs/02-architecture/02-core-flow-and-state.md)
- [事件与派生视图](./docs/02-architecture/03-events-and-derived-views.md)
- [架构约束](./docs/02-architecture/04-architecture-constraints.md)

### 开发与运维

- [Development & Operations 索引](./docs/03-development-and-operations/README.md)
- [本地开发与联调](./docs/03-development-and-operations/01-local-development.md)
- [配置来源与运行依赖](./docs/03-development-and-operations/02-configuration-and-dependencies.md)
- [发布与部署基线](./docs/03-development-and-operations/03-release-and-deployment.md)
- [排障手册](./docs/03-development-and-operations/04-troubleshooting.md)

### 参考手册

- [Reference 索引](./docs/04-reference/README.md)
- [API 与权限矩阵](./docs/04-reference/01-api-and-permissions.md)
- [内部协作接口](./docs/04-reference/02-internal-collaboration.md)
- [端口、依赖与队列清单](./docs/04-reference/03-ports-dependencies-and-queues.md)
- [脚本与入口文件说明](./docs/04-reference/04-scripts-and-entrypoints.md)

### 附录

- [Appendices 索引](./docs/05-appendices/README.md)
- [历史演进背景](./docs/05-appendices/01-historical-evolution.md)
- [关键文件深度导读](./docs/05-appendices/02-file-level-deep-dive.md)
- [术语表](./docs/05-appendices/03-glossary.md)

### 兼容入口

- [旧 `archive` 入口迁移说明](./docs/archive/README.md)

## 常见入口

- 本地启动脚本：[scripts/dev-up.ps1](./scripts/dev-up.ps1)
- 本地日志排查脚本：[scripts/dev-logs.ps1](./scripts/dev-logs.ps1)
- 本地停止脚本：[scripts/dev-down.ps1](./scripts/dev-down.ps1)
- 冒烟脚本：[scripts/smoke-test.ps1](./scripts/smoke-test.ps1)
- Linux 启动脚本：[scripts/run-service.sh](./scripts/run-service.sh)
- 服务器环境变量样例：[scripts/env/server.env.example](./scripts/env/server.env.example)
- 本地 SQL 初始化：[deploy/sql/init.sql](./deploy/sql/init.sql)
- SQL 初始化与迁移策略：[deploy/sql/README.md](./deploy/sql/README.md)
- Nginx 基线：[deploy/nginx/README.md](./deploy/nginx/README.md)
- SAE / MSE / OSS 基线：[deploy/sae/README.md](./deploy/sae/README.md)
- 架构约束测试：[architecture-tests/src/test/java/com/platform/architecture/FinalArchitectureTest.java](./architecture-tests/src/test/java/com/platform/architecture/FinalArchitectureTest.java)

## 当前事实边界

- 当前仓库已不再保留旧单体根目录 `src/`
- 当前对外 API 统一经网关暴露
- 搜索与通知是派生视图，不是内容真源
- 运行时配置来自环境变量和 Nacos；服务自身 `application.yml` 只提供默认值和导入关系
