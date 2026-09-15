# 本地开发与联调

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 首选：S5 隔离环境

使用 PowerShell 7、Java 17、Maven 和 Docker Linux 容器，在后端根目录运行：

~~~powershell
pwsh -File scripts/s5-env.ps1 start
pwsh -File scripts/s5-env.ps1 status
pwsh -File scripts/s5-env.ps1 stop
~~~

脚本支持 up/start/status/stop/down/verify。up 只起中间件；start 负责服务启动；verify 是构建/测试流程，不能替代浏览器验收。-SkipBuild 只可用于确认与当前源码对应且完整构建的产物，不用于跳过失败构建。

- 网关 18080，Auth 18081，Content 18082，Review 18083，Search 18084，File 18085，Notification 18086。
- 配置及运行资料位于 .runtime/s5；local.env 是私有配置，不能提交或复制到普通交付包。
- 已有数据库卷但缺失配置时先恢复凭据，不重新生成凭据碰运气。
- 修改服务前先确认 PID/端口归属；不要重新打包正在运行的同路径 JAR，不停止其他任务的进程。

## 配套前端

演示 checkout 为 semi-overt-frontend，与后端独立。检查其 package.json、Vite 代理与实际启动参数；S5 使用 15173 联调时代理应指向 18080。不修改生产 semi-overt 前端或 semi-overt-springboot 单体来修复演示问题。

## 保留的传统开发方式

scripts/dev-up.ps1、dev-down.ps1、dev-logs.ps1 与根 docker-compose.yml 对应旧的本机环境：8080–8086；中间件默认 3306、6379、8848/9848、5672/15672；日志/PID 在 .codex-runtime。此模式不使用 S5 的 local.env，不能直接套用 S5 端口或验收回执。首次操作前阅读脚本参数及迁移要求。

## Docker 不是 S5

全容器演示使用 scripts/docker-demo.ps1 与 deploy/docker/compose.yml，应用 18000。它与 S5 默认争用 18080。构建、导入和目标机启停按 [Docker 手册](../../deploy/docker/README.md) 执行。

## 验证边界

基础检查：git diff --check、受影响模块测试及前端测试/构建。涉及数据库、会话、Outbox 或审核恢复时另做对应真实依赖回归，并记录日期、提交、产物、执行/跳过测试及环境。旧 smoke-test.ps1 不覆盖全部新增契约，不能用健康检查替代端到端验收。
