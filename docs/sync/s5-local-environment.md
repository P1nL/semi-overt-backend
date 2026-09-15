# S5 本机隔离环境

> semi-overt · 文档整理 2026-09-15 · 阶段资料：保留原日期、契约与验收范围；不代表当前版本、整站或生产验收。 [文档中心](../README.md)

本页准备 S5 的运行环境，不代表 S5 全量验收通过。2026-09-11 更新：S3/S4 已完成本地隔离验收（见 `s3-acceptance.md`、`s4-acceptance.md`）；脱敏迁移对账及 S5 全站验收仍需按计划完成。本机环境配置随成果单独提交，不包含 `.runtime/s5/local.env` 凭据。

## 隔离边界

- 只使用 Docker Compose 项目 `semi-overt-s5`，独立网络与命名数据卷。
- 不复用本机 3306、单体 MySQL 3307、Aearn Redis 6379 或单体后端 8080。
- 中间件端口和 Java HTTP 端口只绑定 `127.0.0.1`。Redis/Nacos 的无认证配置仅用于本机隔离开发，不能部署生产或暴露到局域网。
- Nacos 使用独立 namespace `semi-overt-s5`、group `NOW_DEMO`；七服务真实注册发现，不用 S2 harness 替身。
- 数据库迁移仅允许 `127.0.0.1:13306/content_platform`，使用专属应用账号。新库从空库执行正式迁移，不挂载旧 init.sql，不自动 baseline/repair，不读取或复制单体业务数据。
- 随机生成的密码、JWT 密钥、内部令牌仅保存在 `D:\works\semi-overt-backend\.runtime\s5\local.env`，已有 `.gitignore` 排除。脚本不打印秘密，也不修改系统级环境变量。
- `down` 不删除数据卷；不要单独删除 local.env，否则原卷中的密码不会随重新生成的配置改变。

## 本机地址

| 组件 | 地址/端口 |
|---|---|
| MySQL 8.0.46 | `127.0.0.1:13306`，库 `content_platform`，账号 `semi_s5` |
| Redis 7.2.7 | `127.0.0.1:16379` |
| RabbitMQ 3.13.7 | AMQP `127.0.0.1:15673`；控制台 `http://127.0.0.1:15683` |
| Nacos 2.3.2 | `http://127.0.0.1:18848/nacos`；客户端 gRPC `19848` |
| 本地邮箱 Mailpit | `http://127.0.0.1:18025`；SMTP `127.0.0.1:11025` |
| Gateway | `http://127.0.0.1:18080` |
| auth / content / review | `18081` / `18082` / `18083` |
| search / file / notification | `18084` / `18085` / `18086` |
| 专用前端开发入口 | `http://localhost:15173` |

版本固定用于当前仓库联调，不是最新版本或生产安全基线声明。后续依赖升级另开变更。

## 使用（PowerShell 7）

**2026-09-10 启动易用性修正：不带参数现在默认执行 `start`，会完整启动中间件和七个 Java 服务。**
显式 `up` 仍只启动中间件，并会提示 Java 后端未由该命令启动。四个容器 healthy 不等于整个后端已就绪；应看到最后的 `S5 backend ready` 或 `status` 中七个 `ready=True`。
编译期间脚本会提示正在构建及日志位置，请等待完成；此时不要把尚未监听的 18080 当作业务接口故障。

```powershell
# 仅启动/检查真实中间件，首次会生成本机凭据并拉取镜像
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-env.ps1 up

# 编译 -> 隔离库迁移 -> 顺序启动七服务 -> 每个服务 readiness 验证
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-env.ps1 start

# 已完成本次代码构建时可以跳过重复编译，不允许用旧 jar 冒充新代码验收
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-env.ps1 start -SkipBuild

# 状态、测试、停止 Java、停止整个 S5（保留卷和凭据）
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-env.ps1 status
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-env.ps1 verify
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-env.ps1 stop
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-env.ps1 down
```

`verify` 启用已有 S1/S2 真实 MySQL 测试，测试自己创建随机库，不指向单体实例；这不等于新增了 S3/S4/S5 测试。需要 Docker Desktop（Linux containers）、JDK >=17、Maven 和 pwsh >=7。

若只启动了部分服务，查看日志后先 `stop` 再 `start`，禁止有应用写入时重新迁移。停止仅依据本脚本保存的 PID、创建时间、仓库/服务标记识别 Java 进程，不按端口杀掉其他项目。

## 前端接入

微服务演示前端为 `D:\works\semi-overt-frontend`；其 `vite.config.ts` 默认代理到本机网关 18080，也支持显式环境开关。生产前端 `D:\works\semi-overt` 独立维护，不应在本地演示任务中修改或启动。
在一个专用 pwsh 终端运行：

```powershell
Set-Location D:\works\semi-overt-frontend
$env:VITE_DEV_PROXY_TARGET = 'http://127.0.0.1:18080'
try {
    npm run dev -- --host 127.0.0.1 --port 15173 --strictPort
} finally {
    Remove-Item Env:VITE_DEV_PROXY_TARGET -ErrorAction SilentlyContinue
}
```

`/api` 和 `/static` 一起切换，不会出现 API 用微服务而图片还读单体的混合后端。保持默认相对 `VITE_API_BASE_URL=/api/v1`。S5 使用独立 refresh-cookie 名称；仅本机 HTTP 设置 `Secure=false`。生产 Cookie/TLS 配置不受影响，也没有据此验证生产代理链。

## 尚未配置的外部能力

### 与“连接被拒绝”区分

如果 Vite 显示 `ECONNREFUSED 127.0.0.1:18080`，说明请求尚未建立到网关的 TCP 连接，不能据此判断业务功能是否同步完整。
先执行 `status`：四个中间件 healthy 但七服务 `owned=False, ready=False` 时，Java 后端没有启动，应执行 `start`（现在省略参数也默认完整启动）。
等待网关 readiness 成功后再刷新前端。若 Java 启动失败，查看脚本提示的构建/服务日志，不要通过关闭鉴权或修改业务接口掩盖端口问题。
即使连接恢复，隔离数据库也不会自动拥有线上文章、账号或图片；没有数据与连接失败是两回事。

### 外部提供方

- SMTP 默认指向 `127.0.0.1:11025`，由 Compose 内的 Mailpit 捕获邮件。打开 `http://127.0.0.1:18025` 查看注册/重置的 6 位验证码，再回到前端填写。邮件不会投递到真实外部邮箱；无需个人 SMTP 凭据。验证码校验仍保持开启。Mailpit 端口仅绑定本机，邮件保存在独立数据卷；禁止用于生产。
- Turnstile、Cloudinary 等第三方秘密不从单体或生产复制，不伪造凭据。注册验证码开关仍开启，没有为环境启动绕过风控。
- 文件先采用本机隔离 uploads；Cloudinary 等真实提供方验收及历史图片迁移留待 S4/S5。
- readiness 和少量网关探测只是环境证据，不能替代全站功能、消息乱序/故障注入、迁移对账或生产切换验收。

## 日志

目录：`D:\works\semi-overt-backend\.runtime\s5`。

- `build.log` / `verify.log`：构建及已有自动化测试。
- `migration.log`：隔离库正式迁移或重复校验。
- `<service>.stdout.log` / `<service>.stderr.log`：每服务启动和运行诊断。
- `<service>.process.json`：本脚本拥有的进程回执。

不要将 local.env、原始日志或浏览器凭证直接提交到 Git；对外分享前先脱敏。

## 2026-09-10 本机实测回执

- 四个中间件容器全部 healthy；七个 Java 服务的 `/actuator/health/readiness` 全部 `UP`，并在 S5 namespace 中各注册一个健康实例。
- 空的 S5 应用库通过正式 Flyway 工具执行 V1、V2、V3，现为 v3。没有导入单体数据，没有执行 baseline/repair。
- 多模块 Maven verify：123 tests、0 failures、0 errors、0 skipped；S1/S2 MySQL 环境变量均指向专属 13306 实例。
- 当前前端 `npm run build` 通过。Vite 配置加载器验证默认 `/api`、`/static` 均保持 8080，显式开关后两者均切到 18080。没有把该配置测试算作完整浏览器业务验收。
- 经真实 Gateway/Nacos 路由：首页 `/api/v1/home` 和 `/api/home`、分类 `/api/v1/categories/QUICK/articles`、搜索 `/api/v1/search?keyword=s5` 返回 200；匿名个人资料/草稿请求返回 401。
- 首轮探索误用了前端没有调用的 `/api/v1/categories` 根路径，得到 401。已核对真实前端契约并改测带分类的文章路径；保留 `gateway-smoke.json` 的原始探索结果，不将这个错误猜测列为已修复的业务缺陷。
- 重复 `start -SkipBuild` 正确复用七个就绪实例；进程环境变量恢复测试和并发操作文件锁测试通过。`stop/down` 的实现有所有权校验，本次未为验收而中断已启动的服务。
- 当前保留四个中间件和七个服务运行，供后续 S3/S4/S5 联调；原有单体容器及 Aearn 容器保持运行。独立前端开发服务器未常驻启动，按上文命令接入即可。
- 环境回执：`D:\works\semi-overt-backend\.runtime\s5\environment-receipt.json`；注册实例：`nacos-instances.json`；准确契约探测：`gateway-contract-smoke.json`。
- 拉取镜像时曾遇到 Docker Hub EOF；最终 Docker 原生拉取成功，未更换不明镜像源、未重启 Docker Desktop。临时官方 crane 下载工具的发布 SHA256 已核验，但冗余镜像下载已停止，实际容器使用 Docker 拉取的镜像，未通过备用工具导入。
- 未提交、未推送、未部署生产。SMTP/Turnstile/Cloudinary、S3/S4 能力与 S5 全量业务验收仍不在本次通过范围。

### 同日连接拒绝排查与恢复

2026-09-10 15:42 的现场状态为：前端 15173 正在运行，四个 S5 中间件 healthy，但 18080～18086 没有 Java 服务监听。因此 Vite 的 `ECONNREFUSED 127.0.0.1:18080` 是网关未启动，不是已连接之后的业务契约错误。
已将脚本默认动作从 `up` 改为 `start`，加入构建、迁移、逐服务启动及最终就绪提示；显式 `up` 会警告只启动中间件。
已实测无参数完整启动：构建成功、既有隔离库迁移校验成功、七服务 readiness 全部 UP、Nacos 注册七服务；未重启用户前端。
通过真实网关 18080 以及用户正在运行的前端 15173 分别验证：首页 GET 返回 200；不带 Cookie 的匿名 refresh POST 返回预期 401，不再发生连接拒绝。该检查不使用用户现有浏览器会话，不代表登录和全站业务验收。
已验证显式 `up` 的提示及启动操作锁释放；回执保存在 `D:\works\semi-overt-backend\.runtime\s5\recovery-20260910-receipt.json`，启动日志为同目录 `recovery-20260910.log`。

### 注册联调的另一项前置条件

2026-09-11 实测本地 SMTP 捕获成功，但当前 TurnstileService 在 secret 为空时仍拒绝请求；空配置不代表关闭。网关 register-code 返回 Captcha verification failed 时，应先配置配套的人机验证测试凭据或实现显式、仅限本机 profile 的开发模式。这是发信之前的独立阻断，不能把 Mailpit 可用视为注册全流程通过。
