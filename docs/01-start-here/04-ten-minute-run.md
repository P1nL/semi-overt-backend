# 10 分钟跑通本地环境

适合谁看：需要最快完成本地启动和联调验证的人。  
读完能解决什么问题：在 Windows 本机用最少步骤把中间件、服务和冒烟脚本跑起来。

## 前提

- 已安装 Docker Desktop
- 可用 PowerShell
- 本机已有 Java 与 Maven，或允许脚本使用仓库内 `mvnw.cmd`

## 第 1 步：启动中间件

在仓库根目录执行：

```powershell
docker compose up -d
```

你应该得到这组依赖：

- MySQL `3306`
- Redis `6379`
- Nacos `8848`
- Nacos gRPC `9848`
- RabbitMQ `5672`
- RabbitMQ 管理台 `15672`

## 第 2 步：启动服务

```powershell
.\scripts\dev-up.ps1
```

默认行为：

- 启动前会检查 Docker 依赖和关键端口
- 会把共享模块安装到本地 Maven 仓库
- 会按固定顺序启动 7 个业务服务
- 会把日志写到 `.codex-runtime/logs`
- 会把 PID 写到 `.codex-runtime/pids`

常用参数：

```powershell
.\scripts\dev-up.ps1 -StartupMode fast
.\scripts\dev-up.ps1 -RestartServices
.\scripts\dev-up.ps1 -SkipDocker
```

## 第 3 步：执行冒烟

```powershell
.\scripts\smoke-test.ps1
```

默认会校验：

- Docker 中间件健康
- 服务 `actuator` 健康与信息接口
- 网关公开接口
- 无效 token 的 `401`
- 端到端主链路：注册、登录、草稿、提审、审核通过、通知入库、搜索可见

只想做轻量检查时：

```powershell
.\scripts\smoke-test.ps1 -SkipE2E
```

## 第 4 步：确认成功信号

成功时通常会看到：

- 所有服务的 `/actuator/health` 为 `UP`
- `gateway-service` 在 `8080` 可访问
- 冒烟脚本输出 `E2E smoke passed` 或轻量检查通过信息
- `.codex-runtime/logs` 中能搜到本次 `TraceId`

## 如果失败，先看哪里

- 启动失败：看 [本地开发与联调](../03-development-and-operations/01-local-development.md)
- 配置或中间件失败：看 [配置来源与运行依赖](../03-development-and-operations/02-configuration-and-dependencies.md)
- 冒烟失败：看 [排障手册](../03-development-and-operations/04-troubleshooting.md)
