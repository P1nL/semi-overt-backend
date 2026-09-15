# 配置来源与运行依赖

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 先确认运行模式

| 模式 | 配置入口 |
| --- | --- |
| 传统本机 / 主机脚本 | application.yml、进程环境、scripts/env/server.env.example 和 Nacos |
| S5 | scripts/s5-env.ps1 与私有 .runtime/s5/local.env |
| 全 Docker | deploy/docker/compose.yml 与私有 .runtime/docker-demo.env |

各服务 application.yml 提供默认值及 Nacos import；环境变量、导入文件、profile 与启动参数共同影响最终值。不能仅看到某个 YAML 默认值就断言进程正在使用它。检查注入路径时不要打印真实密钥。

## 核心配置

- 发现：SPRING_PROFILES_ACTIVE、NACOS_SERVER_ADDR、NACOS_NAMESPACE；共享配置包括 shared-common/db/redis/jwt.yaml 和服务配置。
- 数据：DB_URL、DB_USERNAME、DB_PASSWORD；Redis 和 RabbitMQ 的地址/凭据必须与所选栈一致。
- 内部认证：PLATFORM_INTERNAL_TOKEN；Docker/S5 还通过 INTERNAL_TOKEN 组装相关配置，注意两端一致。
- 会话：JWT_SIGN_KEY、AUTH_REFRESH_COOKIE_NAME、AUTH_REFRESH_COOKIE_SECURE、AUTH_REFRESH_IDLE_DAYS、AUTH_REFRESH_ABSOLUTE_DAYS、AUTH_ALLOWED_ORIGINS。
- 安全码：RESET_CODE_PEPPER 必须非空并妥善保管，不在文档中提供固定密钥。
- 邮件与前端链接：MAIL_*、FRONTEND_BASE_URL；本地 Mailpit 不代表真实外发邮件可用。
- 存储：STORAGE_UPLOAD_PATH、STORAGE_ACCESS_PREFIX、存储类型及 OSS/Cloudinary 配置；以 file-service application.yml 和所选实现为准。
- AI：DEEPSEEK_API_KEY、DEEPSEEK_BASE_URL、DEEPSEEK_MODEL、可选代理及 AI_POLISH_*，详见 [AI 润色](../ai-polish.md)。模型名称是仓库默认配置，不是服务商持续可用承诺。

## 迁移与配置变更

数据库迁移由 db-migration/Flyway 管理，不能把 init.sql 当成已有数据库升级脚本。运行前按 [SQL 手册](../../deploy/sql/README.md) 备份、预检并确认目标库。

容器环境变更需要重新创建相关容器；S5 进程需由正确配置重新启动。不要把真实令牌写进示例文件、VITE_*、截图、日志或提交。已有数据卷但环境文件缺失时恢复原凭据，不重新生成覆盖。

## 可用性检查

先查端口/进程和配置，再查依赖，再查 /actuator/health/readiness。Auth 依赖不可用时 Gateway 不应放行；Redis 限流失败也不能静默允许。readiness、队列消费、模型调用和业务完成是不同检查项。
