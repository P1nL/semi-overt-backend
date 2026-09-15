# 脚本与入口文件说明

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

Windows 统一使用 pwsh（PowerShell 7+）。执行前阅读参数、确认运行模式、目标数据库及进程归属。

| 入口 | 用途 / 边界 |
| --- | --- |
| [s5-env.ps1](../../scripts/s5-env.ps1) | up/start/status/stop/down/verify；up 仅中间件 |
| [docker-demo.ps1](../../scripts/docker-demo.ps1) | init/build/pull/up/down/status/logs/export/import，全 Docker 生命周期 |
| [Docker 手册](../../deploy/docker/README.md) | 离线导入、私有配置、迁移、就绪、升级与清理 |
| [dev-up.ps1](../../scripts/dev-up.ps1)、[dev-down.ps1](../../scripts/dev-down.ps1)、[dev-logs.ps1](../../scripts/dev-logs.ps1) | 保留的 8080 本机开发模式 |
| [smoke-test.ps1](../../scripts/smoke-test.ps1) | 传统环境冒烟；不覆盖全部新会话/恢复/AI 契约 |
| [s3-acceptance.ps1](../../scripts/s3-acceptance.ps1) | S3 状态与事件专项；运行会改变测试数据 |
| [s4-verify.ps1](../../scripts/s4-verify.ps1)、[s4-acceptance.ps1](../../scripts/s4-acceptance.ps1) | S4 构建及真实依赖验收 |
| [s5-recovery.ps1](../../scripts/s5-recovery.ps1) | 多实例/故障恢复；先读专项计划，不对共享服务随意注入故障 |
| [flyway-migrate.ps1](../../scripts/flyway-migrate.ps1)、[db-backup.ps1](../../scripts/db-backup.ps1) | 迁移/备份；先确认目标与凭据 |
| [run-service.sh](../../scripts/run-service.sh) | 保留的 Linux 单服务 JAR 启动 |
| [SQL 手册](../../deploy/sql/README.md) | 初始化、迁移与数据保护 |
| [架构测试](../../architecture-tests/src/test/java/com/platform/architecture/FinalArchitectureTest.java) | 模块、Feign、事件基础设施和入口边界 |

三套 Compose 分别是根 docker-compose.yml、docker-compose.s5.yml、deploy/docker/compose.yml。不要在错误目录用裸 docker compose 操作另一套环境。

历史 .cmd 包装不作为本手册推荐入口，直接用 pwsh 调用 .ps1。现行 [Nginx 示例](../../deploy/nginx/now-demo.conf) 保留旧文件名以匹配仓库；项目名称统一 semi-overt。
