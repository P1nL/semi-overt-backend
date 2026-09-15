# semi-overt 首次运行

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

“十分钟”不是耗时承诺；首次构建和下载取决于本机环境。先选一种运行模式，不要混用端口、凭据和数据卷。

## 前提

Windows 使用 PowerShell 7（pwsh），不用 Windows PowerShell 5.1。Docker 必须运行 Linux 容器。S5 本机构建还需要 Java 17 和 Maven；全 Docker 模式不要求宿主机安装 JDK、Maven 或 Node。

## 方式一：全 Docker 演示

在后端仓库根目录的 pwsh 中执行；构建会使用明确指定的演示前端，不使用生产 checkout：

~~~powershell
pwsh --version
docker info
pwsh -File ./scripts/docker-demo.ps1 init
pwsh -File ./scripts/docker-demo.ps1 build -FrontendPath D:/works/semi-overt-frontend
pwsh -File ./scripts/docker-demo.ps1 up
pwsh -File ./scripts/docker-demo.ps1 status
~~~

应用默认 localhost:18000，Mailpit 127.0.0.1:18025。上面是源码构建；离线目标机应使用 [Docker 手册](../../deploy/docker/README.md) 的 import 流程，而不是重新 build。

## 方式二：S5 源码联调

~~~powershell
pwsh -File ./scripts/s5-env.ps1 start
pwsh -File ./scripts/s5-env.ps1 status
Invoke-RestMethod http://127.0.0.1:18080/actuator/health/readiness
~~~

start 启动中间件、准备迁移和服务；up 只启动中间件，不等于七个 Java 服务已启动。配置在 .runtime/s5/local.env。前端单独按其仓库脚本启动并指向 18080。

## 停止与成功标准

- S5：pwsh -File scripts/s5-env.ps1 stop；需要连中间件一起停止时用 down。
- Docker：pwsh -File scripts/docker-demo.ps1 down。
- 不执行 down -v，不删除数据卷或私有配置来“修复”启动。
- readiness 只证明就绪；完整演示还应检查注册邮件、登录、草稿保存、提审/审核、通知、搜索和图片读取。
- 模型润色需要独立配置与验证，普通健康检查不能证明模型可用。

保留的 8080 开发流程见[本地开发](../03-development-and-operations/01-local-development.md)。
