# 排障手册

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 先确定操作对象

先记录模式、端口、PID/容器、源码/镜像版本。传统开发看 .codex-runtime；S5 看 .runtime/s5；Docker 用 scripts/docker-demo.ps1 status/logs。不要把 Docker 持有的端口误判为某个 Java 服务，也不要停止不属于本轮的进程。

| 症状 | 检查顺序 |
| --- | --- |
| PowerShell 脚本失败 | 确认 pwsh / Core 7+，不使用 Windows PowerShell 5.1 |
| 服务未启动 | S5 up 只起中间件；确认是否执行 start，检查构建、迁移、端口与日志 |
| Auth 启动失败 | RESET_CODE_PEPPER、JWT/内部令牌、数据库和 Nacos；Docker 修改环境后重新创建 Auth |
| 登录/刷新异常 | refresh Cookie、来源白名单、Secure、会话数据库与 Auth 权威接口；不要回退旧 New-Token 协议 |
| 503 | 定位 Auth、预算、Redis、Content 或模型依赖；服务不可用不等于会话永久失效 |
| 409 | 读取新版本或决定状态，不强制覆盖，不换 decisionId 掩盖冲突 |
| 审核超时 | 保留同 decisionId，查 PROCESSING/FINAL/CONFLICT 和 Content 决定记录，再查消息 |
| 搜索/通知不更新 | Content 真源 → Outbox 确认 → Rabbit → Inbox/业务事务 → API/前端 |
| 图片 404 或返回 HTML | 文件存在及挂载、File 路由、/static/uploads/ 代理是否误落 SPA fallback、状态/MIME |
| 润色 503/超时 | 密钥是否注入 Content、实际模型地址/名称、代理和 TLS；见 AI 文档 |
| 修改配置无效 | 检查 profile/Nacos/环境变量与实际容器或 JAR，不仅看源码默认值 |

## 安全恢复

- 中间件数据版本不兼容时先备份和确认恢复方案，不默认删卷重建。
- 不执行 docker compose down -v，不删除 .runtime 私有配置，不打印令牌/Cookie/密码。
- 不用关闭鉴权、trust-all TLS、模拟模型文本或无限重试掩盖故障。
- Linux JAR 启动失败检查 Java、构建产物和环境；不要选择来源不明的 target JAR。
- 静态资源和 AI 请求要验证各自代理超时/路径，不因为首页能打开就宣布全站正常。

## 恢复后验证

分别记录迁移退出码、readiness、注册/登录、版本化草稿、同 key 审核恢复、唯一通知、搜索和图片读取。历史专项脚本可参考 [S5 回执](../sync/s5-recovery-acceptance.md)，但回执数字不能作为本次恢复结果。
