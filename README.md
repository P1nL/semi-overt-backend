# now-demo

这是一个面向内容创作、审核、通知和搜索链路的 Spring Boot 多模块微服务示例仓库。当前真实运行主实现是父 [pom.xml](./pom.xml) 下的 Maven 多模块工程，核心链路为 `gateway -> auth/content/review/search/file/notification`。

## 项目状态

- 当前真实结构：父 [pom.xml](./pom.xml) 下的多模块工程
- 当前本地运行形态：`docker-compose.yml` + [scripts/dev-up.ps1](./scripts/dev-up.ps1)
- 当前 Linux 发布基线：[scripts/run-service.sh](./scripts/run-service.sh) + `java -jar`
- 可选外层 HTTP 入口：[deploy/nginx/now-demo.conf](./deploy/nginx/now-demo.conf)
- 当前烟雾验证入口：[scripts/smoke-test.ps1](./scripts/smoke-test.ps1)

重要说明：

- 根目录旧单体 `src` 已经移除，不再保留历史代码残留。
- 本地 MySQL 初始化 SQL 现在位于 [deploy/sql/init.sql](./deploy/sql/init.sql)。
- 当前正式源码入口在各模块目录：[gateway-service](./gateway-service)、[auth-service](./auth-service)、[content-service](./content-service)、[review-service](./review-service)、[search-service](./search-service)、[file-service](./file-service)、[notification-service](./notification-service)、[common](./common)。

## 模块结构

- `gateway-service`：公网统一入口，负责鉴权、路由、限流、TraceId 透传
- `auth-service`：注册、登录、找回密码、用户资料、内部用户摘要接口
- `content-service`：首页、分类、文章创建、草稿、提审、详情、内容域内部接口
- `review-service`：审核任务、审核动作、审核日志、审核任务投影
- `search-service`：公开搜索接口、Elasticsearch 索引同步
- `file-service`：图片上传、本地静态文件访问映射
- `notification-service`：消费文章状态事件，生成通知与投递记录
- `common`：共享常量、内部 DTO、事件模型、公共支持代码

## 文档导航

当前主文档分为三组：

- 项目总览
  - [01 项目概览](./docs/01-overview/01-project-overview.md)
  - [02 模块地图](./docs/01-overview/02-module-map.md)
  - [03 核心业务链路](./docs/01-overview/03-main-flows.md)
- 后端导读
  - [01 API 与鉴权](./docs/02-backend-guide/01-api-and-auth.md)
  - [02 服务职责与边界](./docs/02-backend-guide/02-service-responsibilities.md)
  - [03 数据与事件模型](./docs/02-backend-guide/03-data-and-events.md)
  - [04 改需求入口指南](./docs/02-backend-guide/04-change-entry-guide.md)
- 运行与交付
  - [01 本地开发与联调](./docs/03-runtime-and-delivery/01-local-development.md)
  - [02 运行配置说明](./docs/03-runtime-and-delivery/02-runtime-configuration.md)
  - [03 上线流程 Runbook](./docs/03-runtime-and-delivery/03-release-runbook.md)
  - [04 上线检查与回滚](./docs/03-runtime-and-delivery/04-release-checklist-and-rollback.md)

当前逐文件详细讲解沿用旧路径承载：

- [backend-understanding：逐文件详细讲解](./docs/archive/backend-understanding/README.md)

真正的历史资料保留在：

- [distributed-refactor：历史改造背景](./docs/archive/distributed-refactor/README.md)

## 本地快速启动

### 1. 启动中间件

```powershell
docker compose up -d
```

默认会启动：

- MySQL `3306`
- Redis `6379`
- Nacos `8848`
- RabbitMQ `5672`
- Elasticsearch `9200`

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

### 3. 跑烟雾验证

```powershell
.\scripts\smoke-test.ps1
```

这条脚本会校验：

- 中间件健康状态
- 所有服务的 `/actuator/health` 和 `/actuator/info`
- 网关公开路由
- 无效 token 的 `401`
- “注册 -> 提交审核 -> 审核通过 -> 通知落库 -> 搜索可见”链路

## Linux 发布基线

当前仓库默认的正式发布基线不是容器化业务服务，而是 Linux 主机上的 `java -jar`：

```bash
cp ./scripts/env/server.env.example ./scripts/env/server.env
./scripts/run-service.sh gateway-service server ./scripts/env/server.env
```

发布和回滚细节见：

- [上线流程 Runbook](./docs/03-runtime-and-delivery/03-release-runbook.md)
- [上线检查与回滚](./docs/03-runtime-and-delivery/04-release-checklist-and-rollback.md)
