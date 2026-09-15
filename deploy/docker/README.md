# Semi-Overt 离线 Docker 部署与配置手册

> 核对日期：2026-09-12。适用范围：当前微服务演示离线包、Windows 目标主机、PowerShell 7、Docker Desktop 的 Linux 容器模式。
> 本手册不是生产公网部署方案，也不包含真实密码、API 密钥或现有业务数据。

## 阅读顺序与重要边界

- **目标机第一次部署**：按第 1～8 节顺序执行。AI 不需要时可跳过密钥配置，其他功能仍可验收。
- **目标机以前运行过本项目**：先看第 3 节的数据卷检查；必须沿用与该机现有数据卷匹配的配置。
- **部署后缺少账号、管理员或文章**：看第 8、11、13 节，镜像导入不等于业务数据迁移。
- **报错排查**：看第 12 节。不要先删数据卷、改数据库密码或跳过迁移。
- **开发机重新制作交付包**：看第 14 节。目标机运行不需要源码、Java、Maven、Node.js 或 npm。

四条必须记住的规则：

1. `images.tar` 只包含镜像，不包含本机数据库、上传文件、已注册账号或真实密钥。
2. 首次部署生成目标机自己的配置；已有数据卷却丢失原配置时，应恢复原配置，不能重新生成密码碰运气。
3. “离线部署”只表示核心应用不需要现场拉取镜像；DeepSeek、真实外发邮件和公网人机验证等外部服务仍需要联网。
4. 修改容器环境变量后要重新创建相关容器；仅执行 `restart` 不会把新的变量注入已有容器。参见参考资料 1。

## 1. 交付目录与应携带的文件

本文统一使用目标目录 `D:\semi-overt-offline`。若实际路径不同，请替换第 3 节的 `$Package`。

交付目录应包含：

```text
D:\semi-overt-offline\
├── images.tar
├── compose.yml
├── docker-demo.ps1
├── .env.example
└── README.md
```

- 从已经在开发机验证过的目录复制包时，**不要把开发机的 `.runtime` 目录、配置备份、数据库导出或上传文件混进普通镜像交付包**。
- 目标机自己的 `.runtime\docker-demo.env` 是启动后长期保留的私有配置，应单独安全备份。
- 目标机已有部署时，更新上述交付文件也不能覆盖或删除目标机原来的 `.runtime`。
- 若本次交付目标是完整迁移已有数据，应另行交付受保护的数据备份及恢复方案，不能只复制这五个文件。

当前导出脚本包含以下核心镜像：

| 用途 | 镜像 |
| --- | --- |
| 七个 Java 服务及一次性数据库迁移 | `ghcr.io/p1nl/semi-overt-backend:demo` |
| 前端 | `ghcr.io/p1nl/semi-overt-frontend:demo` |
| MySQL | `mysql:8.0.46` |
| Redis | `redis:7.2.7` |
| RabbitMQ | `rabbitmq:3.13.7-management` |
| Nacos | `nacos/nacos-server:v2.3.2` |
| 本地邮件收件箱 | `axllent/mailpit:v1.27.4` |

`adminer:5.4.2` 是可选工具镜像，**不在默认离线包中**，不是启动核心应用的必需品。

## 2. 目标主机需要提前准备什么

### 2.1 软件、架构与资源

- 安装 PowerShell 7，使用 `pwsh`，不要使用 Windows PowerShell 5.1。
- 安装并启动 Docker Desktop，启用 Linux 容器引擎。按 Docker 官方要求提前准备硬件虚拟化、WSL 2 或所选虚拟化后端。参见参考资料 2、3。
- 真正断网的目标机，应提前准备并完成 Docker Desktop、PowerShell、WSL 等安装；`images.tar` 不包含这些安装程序。
- 本次核对的后端镜像为 `linux/amd64`。ARM 主机需要另外确认全部镜像兼容性，不能直接认为本包已完成 ARM 验证。
- 预留镜像导入、解压、容器日志、数据库与上传文件所需磁盘空间。已有其他容器运行时，也要确认 Docker 可用内存足够；本文不把某个未经压测的数值当作最低资源承诺。
- 如需 AI 润色：准备有效的 DeepSeek API Key，并确保目标机容器能够连接所配置的模型地址。

在 PowerShell 7 中执行：

```powershell
$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -lt 7) {
    throw '请关闭当前终端，改用 PowerShell 7 / pwsh。'
}

$PSVersionTable | Select-Object PSEdition, PSVersion

docker info --format 'OSType={{.OSType}} Architecture={{.Architecture}}'
if ($LASTEXITCODE -ne 0) { throw 'Docker Engine 不可用，请先启动 Docker Desktop。' }

docker compose version
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose 不可用，请完成 Docker Desktop 安装。' }

$dockerOs = docker info --format '{{.OSType}}'
if ($LASTEXITCODE -ne 0 -or $dockerOs -ne 'linux') {
    throw '当前不是可用的 Linux 容器引擎，请先切换并确认 Docker Desktop 就绪。'
}
```

### 2.2 端口与访问范围

| 入口 | 默认地址 | 说明 |
| --- | --- | --- |
| 应用 | `http://localhost:18000` | 在目标主机浏览器打开 |
| 网关就绪检查 | `http://127.0.0.1:18080/actuator/health/readiness` | 默认只绑定宿主机回环地址 |
| Mailpit | `http://127.0.0.1:18025` | 注册、找回密码等测试邮件在这里查看 |
| Adminer | `http://127.0.0.1:18026` | 仅手动启用可选工具后可用 |

MySQL、Redis、RabbitMQ、Nacos 默认只在 Compose 网络中互相通信，没有发布可直接使用的宿主机管理端口。不要把 S5 开发环境的 `13306`、`18848` 等端口套用到此包。

**当前前端端口声明没有限定回环地址，会发布到宿主机网络接口。**本文的登录来源和链接配置仍面向 `localhost`，不应把当前演示配置直接开放到公网。若只在目标机本地演示，可在 `D:\semi-overt-offline\compose.yml` 的 `frontend.ports` 中把端口映射改为下面这一行，再启动：

```yaml
- "127.0.0.1:${APP_PORT:-18000}:8080"
```

局域网多人访问涉及来源白名单、链接地址、访问控制和防火墙；不能只把浏览器地址换成目标机 IP 就视为配置完成。

## 3. 加载离线包与初始化：先检查，再导入

以下第 3～12 节的命令默认在同一个 PowerShell 7 窗口执行。关闭窗口后，请先重新执行本节的“路径变量”代码，再执行后续使用这些变量的命令。

### 3.1 路径变量

```powershell
$ErrorActionPreference = 'Stop'
$Package = 'D:\semi-overt-offline'
$Script = Join-Path $Package 'docker-demo.ps1'
$ComposeFile = Join-Path $Package 'compose.yml'
$EnvFile = Join-Path $Package '.runtime\docker-demo.env'
$ComposeArgs = @(
    '--project-name', 'semi-overt-demo',
    '--env-file', $EnvFile,
    '--file', $ComposeFile
)

foreach ($name in @('images.tar', 'compose.yml', 'docker-demo.ps1', '.env.example', 'README.md')) {
    if (-not (Test-Path -LiteralPath (Join-Path $Package $name) -PathType Leaf)) {
        throw "交付包缺少文件：$name"
    }
}
```

### 3.2 检查是否已经存在本项目的数据卷

**这一步必须放在 `init`、`up`、`status`、`logs` 等脚本操作之前。**当前脚本的多种操作都会调用初始化函数；它本身不会替你拦截“旧数据卷 + 丢失配置”的情况。

```powershell
$existingVolumes = @(docker volume ls --filter 'label=com.docker.compose.project=semi-overt-demo' -q)
if ($LASTEXITCODE -ne 0) { throw '无法检查 Docker 数据卷，停止初始化。' }

$existingVolumes

if ($existingVolumes.Count -gt 0 -and -not (Test-Path -LiteralPath $EnvFile)) {
    throw '发现已有 semi-overt-demo 数据卷，但原配置文件缺失。请先恢复该机原配置；不要执行 init 或删除数据卷。'
}
```

| 检查结果 | 下一步 |
| --- | --- |
| 没有原数据卷，也没有配置 | 全新目标机，导入镜像后执行 `init` |
| 有原数据卷，也有与其匹配的原配置 | 沿用配置，不能用开发机或其他主机的配置替换 |
| 有原数据卷，但原配置丢失 | 停止，恢复原配置；若无法恢复，先制定数据保全与凭据恢复方案 |
| 有配置但没有原数据卷 | 确认这是目标机准备使用的私有配置；不要不知情地沿用开发机凭据 |

两个目录中的脚本都使用相同的 Compose 项目名 `semi-overt-demo`，会操作同一 Docker Engine 上的同名资源。**换目录不等于创建了隔离的新环境。**

### 3.3 导入镜像

```powershell
& $Script import
```

这一步只执行镜像导入，不导入业务数据，也不需要向 GHCR 登录。失败时先检查 `images.tar` 是否复制完整、磁盘是否充足、Docker 是否可用；不要在离线目标机执行 `pull` 或 `build` 来绕过缺失镜像。

若交付方另外提供了 SHA-256 校验值，可运行下面命令并与交付方的值比对。**仅在目标机计算一次哈希而不比对，不能证明复制完整。**

```powershell
Get-FileHash -LiteralPath (Join-Path $Package 'images.tar') -Algorithm SHA256
```

### 3.4 首次生成配置，已有配置则保留

```powershell
if (-not (Test-Path -LiteralPath $EnvFile)) {
    & $Script init
} else {
    Write-Host '保留当前目标机的运行配置，不重新生成。'
}
```

不要把 `.env.example` 整份复制过去覆盖已经生成的文件；模板中的占位密码不能直接使用。

### 3.5 确认核心镜像齐全

下面列表对应本次交付的默认镜像名。若交付方有意使用了其他镜像标签，必须同时核对目标机配置中的两个应用镜像名。

```powershell
$coreImages = @(
    'ghcr.io/p1nl/semi-overt-backend:demo',
    'ghcr.io/p1nl/semi-overt-frontend:demo',
    'mysql:8.0.46', 'redis:7.2.7', 'rabbitmq:3.13.7-management',
    'nacos/nacos-server:v2.3.2', 'axllent/mailpit:v1.27.4'
)
foreach ($image in $coreImages) {
    docker image inspect $image --format '{{.RepoTags}} {{.Os}}/{{.Architecture}}'
    if ($LASTEXITCODE -ne 0) { throw "缺少镜像，请补全离线包：$image" }
}
```

## 4. 哪些配置已经生成，哪些还需要填写

真正使用的配置文件是 `D:\semi-overt-offline\.runtime\docker-demo.env`，不是 `.env.example`，也不是开发机的 `.runtime\s5\local.env`。

### 4.1 配置清单

| 分类 | 配置 | 处理方式 |
| --- | --- | --- |
| 自动生成且必须保留 | `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD` | `init` 生成；已有数据库时不可随意更换 |
| 自动生成且必须保留 | `RABBITMQ_PASSWORD` | `init` 生成；已有 RabbitMQ 数据时要匹配已有账户 |
| 自动生成且必须保留 | `JWT_SIGN_KEY`、`INTERNAL_TOKEN`、`RESET_CODE_PEPPER` | `init` 生成；不要使用模板占位值或跨主机随意混用 |
| 默认已提供 | `BACKEND_IMAGE`、`FRONTEND_IMAGE` | 与导入镜像保持一致，不必填写仓库访问令牌 |
| 默认已提供 | `RABBITMQ_USERNAME=semi_overt` | 默认无需修改 |
| 按需修改 | `APP_PORT`、`GATEWAY_PORT`、`MAILPIT_PORT` | 默认分别为 `18000`、`18080`、`18025` |
| 按需修改 | `ADMINER_PORT` | 默认 `18026`，不启用工具时无需处理 |
| AI 功能需要手填 | `DEEPSEEK_API_KEY` | 只有需要 AI 润色时才必填，见第 5 节 |
| AI 默认已提供 | `DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL` | 默认官方基础地址、`deepseek-flash`；不必因网络故障随意改名 |
| AI 按网络需要填写 | `DEEPSEEK_PROXY_URL` | 默认空，表示不单独指定 HTTP 代理 |
| AI 可选调节 | `AI_POLISH_TIMEOUT_SECONDS`、`AI_POLISH_MAX_TOKENS` | 默认 `45`、`8192`，通常无需修改 |

### 4.2 安全地编辑配置

```powershell
Copy-Item -LiteralPath $EnvFile -Destination "$EnvFile.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
notepad.exe $EnvFile
```

- 每个变量只保留一行；已经存在就修改原行，不要重复追加。
- 保留已有数据库、消息队列和会话密钥，只改需要调整的变量。
- 不要把真实密钥写入 `compose.yml`、`.env.example`、前端 `VITE_*` 变量、镜像或公开文档。
- 不要把完整配置文件、完整 `docker inspect` 或 `docker compose config` 输出原样发到聊天里，它们可能包含明文秘密。
- Compose 可使用当前终端的同名环境变量覆盖 `--env-file` 的值。若文件改了却不生效，先排查终端变量，不要直接认定应用缓存了旧配置。参见参考资料 4。

只显示可能冲突的变量名，不打印其值：

```powershell
$namesToCheck = @(
    'MYSQL_ROOT_PASSWORD', 'DB_PASSWORD', 'RABBITMQ_PASSWORD',
    'JWT_SIGN_KEY', 'INTERNAL_TOKEN', 'RESET_CODE_PEPPER',
    'DEEPSEEK_API_KEY', 'DEEPSEEK_BASE_URL', 'DEEPSEEK_MODEL', 'DEEPSEEK_PROXY_URL',
    'AI_POLISH_API_KEY', 'AI_POLISH_ENDPOINT', 'AI_POLISH_MODEL'
)
$namesToCheck | Where-Object { Test-Path -LiteralPath "Env:$_" }
```

若存在不应覆盖配置的变量，只清除当前终端中相应的项，例如：

```powershell
Remove-Item -LiteralPath 'Env:DEEPSEEK_API_KEY' -ErrorAction SilentlyContinue
```

这不是删除配置文件，也不需要把密钥重新贴到命令行中。

### 4.3 端口冲突

```powershell
Get-NetTCPConnection -State Listen -LocalPort 18000, 18080, 18025 -ErrorAction SilentlyContinue |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

有监听不一定是错误：可能就是本项目已有容器。确认归属后再处理；不要按端口直接杀死未知进程。若选择修改本项目端口，只编辑目标机配置对应的端口变量，并在后续地址示例中使用新端口。

## 5. 配置 AI 润色：密钥、模型与代理

### 5.1 填写密钥

用第 4.2 节的方法打开配置，添加或修改：

```dotenv
DEEPSEEK_API_KEY=替换为目标主机要使用的真实密钥
```

上面的中文是占位说明，不能原样当作密钥。密钥留空不会阻止基础应用部署，但调用 AI 润色时会提示尚未配置。

以下变量通常无需额外写入，只有需要显式指定时才添加；如已存在，应修改原行：

```dotenv
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-flash
DEEPSEEK_PROXY_URL=
AI_POLISH_TIMEOUT_SECONDS=45
AI_POLISH_MAX_TOKENS=8192
```

- `DEEPSEEK_BASE_URL` 是基础地址，不能填完整的 `/chat/completions` 路径。
- 有效密钥可通过服务商的 `/models` 接口确认可用模型；模型列表成功不等于实际润色已经成功。
- 旧的 `AI_POLISH_ENDPOINT` 非空时优先于新的基础地址。如果不是明确使用旧地址覆盖，应将它清空。
- 新的 `DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL` 设置后优先于旧的 `AI_POLISH_API_KEY`、`AI_POLISH_MODEL`。
- AI 会把需要润色的文本发送给配置的外部模型服务；正式使用前确认文本可发送，并确认密钥权限与账户额度。参见参考资料 5。

### 5.2 确实需要代理时才配置

在 Windows Docker Desktop 上，如果目标主机的 HTTP 代理端口为 `7890`，可配置：

```dotenv
DEEPSEEK_PROXY_URL=http://host.docker.internal:7890
```

- `host.docker.internal` 用于容器访问宿主机服务；容器里的 `127.0.0.1` 指向容器自己，不能照搬本机 Java 环境的代理地址。参见参考资料 6。
- 代理必须允许来自 Docker 网络的连接；若代理只接受宿主机回环连接，即使浏览器能上网，容器仍可能连接失败。
- 按代理软件提供的最小必要访问范围配置，不要为了测试把无认证代理暴露到公网。
- 这里需要 HTTP 代理地址，不是 SOCKS 地址，也不能在 URL 中嵌入代理账号密码。
- 没有代理或不需要代理时保持空值。Docker Desktop 的镜像拉取代理设置，不等于 Java 应用已经使用了同一个代理。
- 不要关闭 TLS 证书校验、启用 trust-all 或盲目重复请求来掩盖连接故障。

### 5.3 首次启动与运行后修改的区别

首次部署：保存配置后继续第 6 节即可，不需要先单独启动内容服务。

应用已经运行：修改 AI 配置后，重新创建内容服务容器，不需要重新打包镜像：

```powershell
docker compose @ComposeArgs up -d --no-deps --force-recreate --pull never --no-build --wait --wait-timeout 180 content-service
if ($LASTEXITCODE -ne 0) { throw '内容服务未成功重建，请查看其日志。' }
```

## 6. 启动完整应用

### 6.1 严格离线启动

先静默校验 Compose 配置。这会检查必需变量与配置结构，不启动容器，也不打印展开后的秘密值：

```powershell
docker compose @ComposeArgs config --quiet
if ($LASTEXITCODE -ne 0) { throw '配置校验失败，请补齐报错指出的必需项，再启动。' }
```

推荐使用下面的命令，显式禁止现场拉取镜像或构建：

```powershell
docker compose @ComposeArgs up -d --pull never --no-build --wait --wait-timeout 420
if ($LASTEXITCODE -ne 0) { throw '启动未完成，请先按第 12 节检查日志，不要删除数据卷。' }
```

确认全部镜像已导入后，也可使用封装脚本：

```powershell
& $Script up
```

以上两种方式选择一种即可。脚本没有显式传入 `--pull never`，因此在严格断网验收时优先使用第一种命令。

启动顺序大致为：MySQL、Redis、RabbitMQ、Nacos 等基础服务 → 数据库迁移 → 六个业务服务 → 网关 → 前端。首次初始化需要等待，不要因为 Nacos 暂时为 `starting` 就直接重置数据。

### 6.2 查看完整状态

```powershell
& $Script status
docker compose @ComposeArgs ps -a
docker inspect semi-overt-demo-db-migration-1 --format 'status={{.State.Status}} exitCode={{.State.ExitCode}}'
```

- `db-migration` 是一次性任务，正确状态是 `exited` 且 `exitCode=0`，不是一直运行或一直健康。
- 迁移 `exitCode=1` 会阻止依赖它的服务启动。先看迁移日志，不能跳过它直接拉起业务服务。
- 其他持续运行的服务应进入运行状态，其健康检查应最终通过。

默认地址：

```powershell
Start-Process 'http://localhost:18000'
Start-Process 'http://127.0.0.1:18025'
```

## 7. 验收：不能只看容器已启动

### 7.1 七个 Java 服务的就绪检查

```powershell
$servicePorts = [ordered]@{
    'auth-service' = 8081
    'content-service' = 8082
    'review-service' = 8083
    'search-service' = 8084
    'file-service' = 8085
    'notification-service' = 8086
    'gateway' = 8080
}
foreach ($entry in $servicePorts.GetEnumerator()) {
    Write-Host "检查 $($entry.Key)"
    docker compose @ComposeArgs exec -T $entry.Key curl -fsS "http://127.0.0.1:$($entry.Value)/actuator/health/readiness"
    if ($LASTEXITCODE -ne 0) { throw "服务未就绪：$($entry.Key)" }
    Write-Host ''
}
```

七项都应成功，并返回 `UP`。这里的 `gateway` 是 Compose 服务名；`gateway-service` 是 Java 模块名，不能在 `-Service` 参数中混用。

若使用默认应用端口，还可检查前端：

```powershell
Invoke-WebRequest 'http://localhost:18000/healthz' -TimeoutSec 10 |
    Select-Object StatusCode, Content
```

### 7.2 检查 AI 配置与实际效果

保存配置并按第 5.3 节重建内容服务后，使用页面中的 AI 润色入口验证。不要为了排查把整个容器环境或密钥打印到终端。

变量非空、容器就绪、宿主机浏览器联网、模型列表接口成功，都不能替代一次实际润色请求的验证。

### 7.3 人工验收清单

- [ ] 页面能够打开，登录后刷新页面仍能保持正常会话。
- [ ] 按第 8 节注册普通账号，能在 Mailpit 收到该邮箱对应的验证码。
- [ ] 新建文章、保存草稿、离开后重新打开，正文仍在。
- [ ] 上传一张测试图片，保存后重新打开仍能显示。
- [ ] 如需审核，使用另一个已获授权的管理员账号验证，不用作者本人自审。
- [ ] 如需 AI，用一小段不含敏感信息的正文生成建议，检查完整返回后再决定是否应用。
- [ ] 测试一次 `down` 后再 `up`，确认账号、草稿和图片仍在；执行前确认没有其他人的未保存操作。

AI 验证会调用真实外部服务，可能产生费用；不要用自动循环或重复点击做无边界重试。

## 8. 第一次登录：账号、邮箱验证码与初始内容

### 8.1 没有内置通用账号密码

当前迁移脚本负责创建和升级表结构，没有为全新空库预置可直接登录的通用管理员，也没有把开发机的演示文章打进镜像。不要尝试文档示例中的账号，或自行猜测 `admin/admin`。

全新目标机首页没有开发机原有文章、原账号登录失败，不能直接据此判断镜像导入失败。新目标机需要注册自己的账号，或按单独批准的数据迁移方案恢复已有数据。

### 8.2 注册普通账号并取验证码

1. 在目标主机打开 `http://localhost:18000`，进入注册界面。
2. 填写自己的用户名、邮箱和符合页面要求的密码。仅本机演示时可使用专用测试邮箱，例如 `owner@example.test`；不要将该测试邮箱误认为能接收公网邮件。
3. 点击页面的“发送验证码”。
4. 在目标主机打开 `http://127.0.0.1:18025`，在 Mailpit 中找到发给该邮箱的邮件，读取六位验证码。
5. 回到注册界面填写收到的验证码并提交，之后使用自己的账号登录。

补充说明：

- 当前 Compose 在后端设置了 `REGISTRATION_CODE_REQUIRED=false`、`TURNSTILE_ENABLED=false`，这是本地演示配置，不代表前端删除了验证码流程。
- **当前注册页面仍要求发送并填写邮箱验证码。**正常使用 Mailpit 完成页面流程，不要编造验证码或修改浏览器绕过校验。
- `admin`、`system`、`me` 是后端保留用户名。需要管理账号时可先注册其他用户名，例如 `site_owner`，再由环境负责人按第 13 节安排受控授权。
- 新注册用户默认是普通用户 `USER`，注册成功不等于具有审核权限。
- 忘记密码等测试邮件同样由 Mailpit 接收，不会自动发到真实外部邮箱。

## 9. 其他“缺失配置”如何判断

| 功能 | 本包现状 | 是否必须补充 |
| --- | --- | --- |
| 数据库、Redis、RabbitMQ、Nacos | Compose 内部连接已配置 | 基础演示不需要逐项手填 IP，也不需要创建外部云服务 |
| 邮件验证码 | 发送到内置 Mailpit | 本地演示无需真实 SMTP 密钥；在 Mailpit 查收 |
| 真实外发邮件 | 默认没有配置 | 有此需求才单独配置 SMTP，并验证网络、发件身份、认证和 TLS |
| 人机验证 | 后端演示配置禁用；前端依赖构建时站点键 | 本地演示无需填；公网启用需要另行配置和验收 |
| 图片上传 | 文件服务使用本地持久化卷 `uploads` | 无需填写 OSS、S3 或其他云存储密钥 |
| AI 润色 | 地址和模型有默认值，真实密钥不随包交付 | 按第 5 节补充密钥，按网络情况补充代理 |
| 管理员权限 | 空库无默认管理员 | 需要审核或管理功能时，由负责人提供受控初始化方案，见第 13 节 |
| 开发机账号、文章、图片 | 不在镜像包里 | 如需保留，要另外备份和迁移数据，不是补一个环境变量 |
| Adminer | Compose 定义了工具，但默认包不包含镜像 | 可选，不影响核心应用验收 |

### 9.1 为什么有些变量写进环境文件后没有效果

只有 `compose.yml` 引用并传给容器的变量才会影响容器。当前 `MAIL_HOST=mailpit`、邮件端口、注册开关、Turnstile 开关等在 Compose 中有固定值；仅向 `.runtime\docker-demo.env` 追加同名变量，并不会自动覆盖这些固定值。

如确需真实 SMTP，应同时修改 Compose 的环境变量映射，并按服务商要求填写配置；如确需启用公网人机验证，前端站点键涉及重新构建前端，后端还需配套验证配置。不要把当前关闭验证、使用本地收件箱的演示环境直接当作生产配置。

### 9.2 可选启用 Adminer

如果目标机完全离线且没有 `adminer:5.4.2`，先在联网交付机单独准备：

```powershell
docker pull adminer:5.4.2
if ($LASTEXITCODE -ne 0) { throw 'Adminer 镜像获取失败。' }
docker save --output 'D:\adminer-5.4.2.tar' adminer:5.4.2
if ($LASTEXITCODE -ne 0) { throw 'Adminer 镜像导出失败。' }
```

把额外的镜像文件复制到目标机，再在目标机执行；这部分是可选分支，不要在没有该文件时照搬：

```powershell
docker load --input 'D:\adminer-5.4.2.tar'
if ($LASTEXITCODE -ne 0) { throw 'Adminer 镜像导入失败。' }
docker compose @ComposeArgs --profile tools up -d --pull never --no-build adminer
if ($LASTEXITCODE -ne 0) { throw 'Adminer 启动失败。' }
Start-Process 'http://127.0.0.1:18026'
```

在 Adminer 中选择 MySQL，服务器填 `mysql`、数据库填 `content_platform`，使用目标机自己的数据库账号 `semi_overt` 和对应的 `DB_PASSWORD`。不要填宿主机 `127.0.0.1`，也不要把密码发给他人代填。

## 10. 日常启停、更新配置与日志

### 10.1 停止应用，保留数据

```powershell
& 'D:\semi-overt-offline\docker-demo.ps1' down
```

此命令停止并移除项目容器，保留镜像、数据卷和运行配置。不要擅自追加 `-v`，也不要执行清理全部卷的命令。

### 10.2 再次启动

先启动 Docker Desktop，再执行：

```powershell
& 'D:\semi-overt-offline\docker-demo.ps1' up
& 'D:\semi-overt-offline\docker-demo.ps1' status
```

无需再次 `import` 或 `init`。若需要显式禁止联网拉镜像，重新执行第 3.1 节的变量定义，再用第 6.1 节的严格离线命令。

### 10.3 配置变更

- 只修改 AI 密钥、模型、代理：按第 5.3 节只重建 `content-service`。
- 修改应用端口或其他被多个服务使用的配置：运行完整 `up`，让 Compose 按配置变化重建相关容器，并重新验收。
- 修改已有数据库、RabbitMQ 或会话密钥，不属于一般配置刷新；先制定与持久化状态匹配的变更和回滚方案。
- 只修改本手册、环境文件或 Compose 配置，不需要重新构建应用镜像；修改 Java 或前端源码才涉及重新构建并交付镜像。

### 10.4 查看日志

```powershell
& 'D:\semi-overt-offline\docker-demo.ps1' logs
& 'D:\semi-overt-offline\docker-demo.ps1' logs -Service db-migration
& 'D:\semi-overt-offline\docker-demo.ps1' logs -Service content-service
& 'D:\semi-overt-offline\docker-demo.ps1' logs -Service auth-service
& 'D:\semi-overt-offline\docker-demo.ps1' logs -Service nacos
& 'D:\semi-overt-offline\docker-demo.ps1' logs -Service gateway
```

排查时提供失败服务及对应异常附近的必要日志即可。先检查并遮蔽密钥、密码、邮箱及正文，不要直接发送完整环境文件或容器环境列表。

## 11. 数据保存、备份与跨机迁移边界

### 11.1 哪些内容不在镜像里

| 内容 | 存储位置或处理要求 |
| --- | --- |
| 账号、角色、文章、会话持久化记录等 | MySQL 数据卷 |
| 上传图片 | `uploads` 数据卷，文件服务内路径 `/data/uploads` |
| Redis 状态 | Redis 数据卷；不能假设其中所有数据都是可随意丢弃的缓存 |
| 消息及消息队列状态 | RabbitMQ 数据卷；迁移前需要确认在途消息与事件处理状态 |
| Nacos 状态和日志 | 对应 Nacos 数据卷 |
| 测试邮件 | Mailpit 数据卷 |
| 数据库、服务鉴权及 AI 等秘密 | 目标机的 `.runtime\docker-demo.env` |

`down` 后这些卷仍在；删除 `.runtime` 不会删除卷，因此会产生“旧数据 + 新密码”的风险。`docker save` 不会把上述卷内容装进镜像包。

### 11.2 配置备份与数据保全

编辑前可以按第 4.2 节创建配置备份，也应把目标机配置保存在另外的受控备份位置。不要把备份混入给其他主机的新安装包。

升级数据库、初始化管理员或迁移已有内容前，由有权维护数据的负责人完成数据库与上传文件备份，并验证备份可恢复。还需评估 Redis、RabbitMQ、Nacos 等状态的保留方式；不要把只导出一份 SQL 当作完整的系统迁移。

当前离线包的 `docker-demo.ps1` 没有 `backup`、`restore` 或管理员初始化动作。本文不虚构这些命令，也不把高权限数据库写操作混进首次安装流程。

### 11.3 如果需要完整迁移现有数据

至少要安排：停写和在途事件处理 → 一致的数据与上传文件备份 → 对应配置的安全交接或受控凭据轮换 → 目标机受控恢复 → 迁移兼容性校验 → 七服务和业务验收 → 回滚演练。

只恢复 MySQL、只复制上传目录、直接拷贝运行中的 Docker 卷，或只把旧 `.runtime` 复制到另一台已有数据库的主机，都不能视为完整迁移。本文默认主线是“新目标机导入镜像并初始化空库”，没有替你执行业务数据恢复。

## 12. 常见故障与处理顺序

如果原运行配置已经缺失，先不要调用会自动初始化配置的脚本。下面的 Docker 原生命令不读取项目环境文件，可先用于获取状态和迁移错误：

```powershell
docker ps -a --filter 'label=com.docker.compose.project=semi-overt-demo'
docker logs --tail 200 semi-overt-demo-db-migration-1
```

### 12.1 `db-migration` 退出 1，日志为 `Access denied for user 'semi_overt'`

首先怀疑当前应用密码与已有数据库中的密码不匹配，而不是镜像损坏。即使 MySQL 显示 `Healthy`，也不能据此认定业务账号可以登录。

本次本地验证曾出现：从新目录运行 `init` 生成了新密码，但同一 Docker Engine 上的 `semi-overt-demo_mysql-data` 已经存在。新容器读取了新环境变量，卷里的数据库仍保留原账户密码。MySQL 的既有数据目录不会因为更换初始化环境变量就重置账号密码。参见参考资料 7。

正确处理方式：

1. 保留当前配置和数据卷，读取迁移异常。
2. 找到与这套数据卷匹配、已经核对可信的原配置，而不是随便找另一个环境文件。
3. 备份当前文件，恢复原配置，再重新创建受影响容器。
4. 若原配置确实无法恢复，先进行数据保全与凭据恢复规划，不删除卷，不跳过迁移。

恢复命令模板如下。请先把 `$TrustedEnv` 换成已经核对的原配置路径；模板故意使用不存在的占位路径，避免误操作：

```powershell
$TrustedEnv = 'D:\semi-overt-backups\请替换为已核对的原配置.env'
if (-not (Test-Path -LiteralPath $TrustedEnv -PathType Leaf)) {
    throw '未提供可信原配置，停止恢复。'
}
if (Test-Path -LiteralPath $EnvFile) {
    Copy-Item -LiteralPath $EnvFile -Destination "$EnvFile.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}
New-Item -ItemType Directory -Path (Split-Path -Parent $EnvFile) -Force | Out-Null
Copy-Item -LiteralPath $TrustedEnv -Destination $EnvFile -Force

docker compose @ComposeArgs up -d --pull never --no-build --wait --wait-timeout 420
if ($LASTEXITCODE -ne 0) { throw '恢复配置后仍未启动成功，请继续查看迁移日志。' }
```

本次开发机已核对过的原配置在 `D:\works\semi-overt-backend\.runtime\docker-demo.env`。这个路径仅适用于那套已经验证过的本机数据卷，不是任意目标主机的通用修复文件。

### 12.2 `Data preflight refused`、迁移校验失败或历史版本不一致

这是另一类问题，不要与密码错误混为一谈。先保存日志并备份数据库，再确认实际行数据、迁移历史与镜像版本。不要自动执行 Flyway repair/baseline、删除迁移历史、绕过校验或直接批量改文章状态。

```powershell
& $Script logs -Service db-migration
docker compose @ComposeArgs ps -a
```

### 12.3 Nacos 显示 `starting` 或依赖暂时未就绪

```powershell
docker inspect semi-overt-demo-nacos-1 --format '{{.State.Status}} {{.State.Health.Status}}'
& $Script logs -Service nacos
```

等待后若已 `healthy`，说明之前只是启动过程中的状态；不要继续把它当作独立故障。持续不健康时结合日志检查资源和持久化状态，不要先重置数据或无限延长超时。

### 12.4 AI 润色不可用

| 现象 | 优先检查 |
| --- | --- |
| 提示尚未配置 | 是否编辑了实际运行配置，是否填了真实密钥、是否重建了内容服务 |
| 文件已改但似乎仍用旧值 | 终端环境变量覆盖、重复配置行、旧地址变量覆盖、只 restart 未重建 |
| `SSLHandshakeException` 或安全连接失败 | 容器到服务商的 TLS 连接、宿主代理是否可由容器访问；不要禁用证书校验 |
| 连接拒绝、DNS 错误 | 是否把代理错误写成容器自己的回环地址，代理是否启动、解析和网络是否可用 |
| 请求超时 | 检查服务商和代理连通性及响应耗时，不要只增加前端等待时间 |
| 连接通但生成失败 | 核对账户权限、额度、模型名称及响应校验，不要让前端应用不完整结果 |

```powershell
& $Script logs -Service content-service
```

宿主机浏览器能访问网站，或者一次 `/models` 返回成功，都不能证明实际润色可用。最终以真实、少量、非敏感正文的端到端验证为准。

### 12.5 其他常见现象

| 现象 | 处理 |
| --- | --- |
| Docker Engine 不可用 | 先启动 Docker Desktop，并确认当前 Docker context 是预期引擎；不要未确认就切换或清理别的项目 |
| `port is already allocated` | 检查端口归属，调整目标配置中的相应端口；不要杀死未知进程 |
| 镜像不存在、导入 EOF、空间不足 | 核对包完整性和磁盘，再补传镜像；断网目标机不执行 pull 补救 |
| 打开首页但注册收不到公网邮件 | 默认邮件进入 Mailpit，不是真实外发 SMTP |
| 注册账号没有审核权限 | 默认角色是 USER，见第 13 节 |
| 首页没有开发机文章 | 空库没有迁移业务数据；需要新建内容或单独恢复数据 |
| Adminer 打不开 | 默认未启用且镜像不随包，见第 9.2 节 |
| 换目标机 IP 后登录或跨域失败 | 当前来源配置面向 localhost，需要单独完成局域网或域名部署配置 |
| 换目录后数据库密码不对 | 检查是否误删或覆盖原运行配置，以及是否复用了同名 Compose 数据卷 |

## 13. 可选：初始管理员与审核演示

**当前离线包没有通用管理员密码，也没有已经提供的 `create-admin` 或 `seed` 命令。**不要自行猜测这些命令，不能通过新增一个环境变量自动获得管理员权限。

如果此次交付需要管理员或审核功能，应把下面事项作为独立的交付验收项，由有权维护该环境的负责人完成：

1. 在目标机页面注册并确认要授予管理权限的专用账号，例如使用自己的用户名和邮箱；不要使用共享弱密码账号。
2. 在授权或初始化数据前完成数据库备份，并确认回滚方法。
3. 由交付方提供经审查、限定到该指定账号的初始化方案；权限修改不能混入所有人都会执行的镜像导入脚本。
4. 完成后退出旧会话，重新登录，验证账号身份、角色和对应管理操作，而不是只看页面是否显示一个按钮。
5. 使用普通作者账号提交文章，使用另一个管理员审核。系统保留作者与审核人的分离约束，不应为了演示关闭自审限制。

普通账号注册、草稿编辑、图片上传和 AI 润色不以管理员初始化为前提；若没有完成这一项，只能报告基础功能已验收，不能声称管理员审核演示已经就绪。

## 14. 开发机重新制作或更新离线包

本节在有两个源码仓库的开发机执行，不在无源码目标机执行。构建和获取缺失镜像需要联网；构建发生在 Docker 内，不需要先停止宿主机的 Java 开发服务。

```powershell
$ErrorActionPreference = 'Stop'
$BuildScript = 'D:\works\semi-overt-backend\scripts\docker-demo.ps1'
$BuildEnv = 'D:\works\semi-overt-backend\.runtime\docker-demo.env'
$NewPackage = "D:\semi-overt-offline-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

$buildHostVolumes = @(docker volume ls --filter 'label=com.docker.compose.project=semi-overt-demo' -q)
if ($LASTEXITCODE -ne 0) { throw '无法检查开发机的已有数据卷。' }
if ($buildHostVolumes.Count -gt 0 -and -not (Test-Path -LiteralPath $BuildEnv)) {
    throw '开发机已有项目数据卷但原配置缺失，请先恢复原配置。'
}

& $BuildScript init
& $BuildScript build -FrontendPath 'D:\works\semi-overt-frontend'
& $BuildScript export -OutputPath $NewPackage

Write-Host "新的交付目录：$NewPackage"
Get-FileHash -LiteralPath (Join-Path $NewPackage 'images.tar') -Algorithm SHA256
```

- `export` 要求输出目录为空，使用带时间戳的新目录，不要清空已验证的旧包来节省步骤。
- `export` 会把本手册作为 `README.md` 一并复制。文档更新本身不需要重新构建镜像；已导出的包只需同步新版 README。
- 如果把新包再次用于本机验证，注意它仍操作同一个 `semi-overt-demo` 项目；不能因为目录新就重新生成与旧卷不匹配的配置。
- 更新已有目标机时，保留目标机私有配置和数据备份，再导入新镜像并运行 `up`。数据库升级前先审查兼容性；回退镜像不等于数据库已自动回退。
- 如选择联网仓库分发，脚本另有 `pull` 操作，但前提是镜像已发布且目标机有读取权限；这不是默认离线交付路径，也不代表当前 GHCR 镜像已经公开可读。

## 15. 交付验收记录与参考资料

### 15.1 建议填写的验收记录

| 检查项 | 目标机记录 |
| --- | --- |
| 目标机、操作者、验证日期 | 自行填写，不在共享文档中写秘密 |
| 包版本与交付方提供的镜像归档校验值 | 自行填写并完成比对 |
| Docker / Compose / PowerShell 版本与架构 | 自行填写 |
| 原配置与数据卷关系已确认 | 是 / 否 |
| 数据库迁移退出码 | 应为 0 |
| 七个 Java 服务就绪 | 应全部通过 |
| 前端、注册与 Mailpit 验证 | 通过 / 不通过 |
| 草稿与图片保存及重启后持久化 | 通过 / 不通过 |
| 管理员审核（如需要） | 通过 / 尚缺受控初始化 / 不适用 |
| AI 实际生成（如需要） | 通过 / 未配置 / 不适用，不能用变量非空代替 |
| 数据与配置备份位置 | 仅记录受控位置，不填写密码 |

本手册依据当前仓库与离线包的脚本、Compose、认证和迁移实现核对。文档中的命令做静态语法检查，不意味着已经在任意目标机执行；本机成功也不能代替目标机验收。

### 15.2 官方参考资料

参考资料用于解释外部工具的行为。项目特定变量、镜像标签、端口和账号流程以本包内脚本、配置及当前代码为准。

1. Docker Compose `up`：容器重建、等待就绪和禁止拉取等选项。
   `https://docs.docker.com/reference/cli/docker/compose/up/`
2. Docker Desktop Windows 安装与系统前提。
   `https://docs.docker.com/desktop/setup/install/windows-install/`
3. Microsoft PowerShell Windows 安装。
   `https://learn.microsoft.com/powershell/scripting/install/installing-powershell-on-windows`
4. Docker Compose 环境变量优先级。
   `https://docs.docker.com/compose/how-tos/environment-variables/envvars-precedence/`
5. DeepSeek API 使用说明。
   `https://api-docs.deepseek.com/`
6. Docker Desktop 从容器访问宿主机服务。
   `https://docs.docker.com/desktop/features/networking/networking-how-tos/`
7. MySQL Docker 部署：已有数据目录与初始化环境变量。
   `https://dev.mysql.com/doc/refman/8.0/en/docker-mysql-more-topics.html`
