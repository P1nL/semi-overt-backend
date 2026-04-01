# 本地开发与联调

适合谁看：需要在 Windows 本机启动仓库、联调或验证修复的人。  
读完能解决什么问题：知道本地依赖、服务端口、启动顺序、日志位置和常用脚本参数。

## 本地运行基线

本地开发默认分两段：

1. `docker compose up -d` 拉起中间件
2. `.\scripts\dev-up.ps1` 拉起 7 个业务服务

## 中间件清单

[docker-compose.yml](../../docker-compose.yml) 默认提供：

- MySQL `3306`
- Redis `6379`
- Nacos HTTP `8848`
- Nacos gRPC `9848`
- RabbitMQ `5672`
- RabbitMQ 管理台 `15672`

MySQL 初始化脚本：

- [deploy/sql/init.sql](../../deploy/sql/init.sql)

## 本地服务端口

- `gateway-service`：`8080`
- `auth-service`：`8081`
- `content-service`：`8082`
- `review-service`：`8083`
- `search-service`：`8084`
- `file-service`：`8085`
- `notification-service`：`8086`

## `dev-up.ps1` 的实际行为

[scripts/dev-up.ps1](../../scripts/dev-up.ps1) 会做这些事：

- 自动设置默认 `NACOS_SERVER_ADDR`
- 若未设置 `NACOS_NAMESPACE`，会写入当前脚本内置的开发命名空间 ID
- 检查中间件端口冲突
- 在 `.codex-runtime` 下准备日志、PID 与 Maven 配置
- 安装父 POM 与共享模块到本地 Maven 仓库
- 按 `stable` 或 `fast` 模式启动服务
- 用 `/actuator/health` 与 `/actuator/info` 判断服务是否就绪

## 常用启动方式

稳定模式：

```powershell
.\scripts\dev-up.ps1
```

快速模式：

```powershell
.\scripts\dev-up.ps1 -StartupMode fast
```

跳过 Docker：

```powershell
.\scripts\dev-up.ps1 -SkipDocker
```

强制重启全部服务：

```powershell
.\scripts\dev-up.ps1 -RestartServices
```

## 日志与运行时目录

- 日志：`.codex-runtime/logs`
- PID：`.codex-runtime/pids`
- 启动状态：`.codex-runtime/dev-up-state.json`

## 联调入口

默认统一联调地址：

```text
http://127.0.0.1:8080
```

前端、测试或 Postman 应优先走网关，不要直接把业务服务当成外部 API 入口。
