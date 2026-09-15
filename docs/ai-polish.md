# 编辑页 AI 润色

## 配置

这是微服务演示仓库的功能，不涉及生产前端或单体部署。模型调用已改为 **LangChain4j 1.20.0 + DeepSeek**，默认使用 deepseek-flash、关闭思考模式。默认模型地址和名称已提供，未设置密钥时返回 503，不提供模拟润色兜底。

Maven 依赖是 dev.langchain4j:langchain4j-open-ai:1.20.0，使用 OpenAiChatModel 调用 DeepSeek 的 OpenAI 兼容接口，并不调用 OpenAI 服务。没有引入 Spring Boot 自动配置 starter，也没有升级项目的 Spring Boot 或 Java 基线。

content-service 使用以下后端环境变量：

| 变量 | 含义 |
| --- | --- |
| DEEPSEEK_API_KEY | 必填，DeepSeek 模型密钥；只放在后端环境，不进入 VITE_*、源码或浏览器 |
| DEEPSEEK_BASE_URL | 默认 https://api.deepseek.com；是基础地址，不包含 chat/completions；自建代理可覆盖，仅本地测试允许 loopback HTTP |
| DEEPSEEK_MODEL | 默认 deepseek-flash；应以当前 API 密钥通过 /models 返回的模型列表为准 |
| DEEPSEEK_PROXY_URL | 可选 HTTP 代理，例如本机开发环境的 http://127.0.0.1:7890；不允许把代理账号密码嵌入 URL |
| AI_POLISH_ENDPOINT | 兼容旧配置：完整 chat/completions 地址；非空时优先于 DEEPSEEK_BASE_URL，并自动转换为 SDK 基础地址 |
| AI_POLISH_API_KEY / AI_POLISH_MODEL | 兼容旧配置：对应 DEEPSEEK_API_KEY / DEEPSEEK_MODEL 未设置时回退使用 |
| AI_POLISH_TIMEOUT_SECONDS | 默认 45 秒，服务端限制 5–90 秒 |
| AI_POLISH_MAX_TOKENS | 默认 8192，限制 256–16384，需与模型支持上限一致 |

LangChain4j 负责构造消息、鉴权请求与 ChatResponse 映射，业务代码不再手工拼接 Chat Completions 请求体。请求使用非流式 Chat Completions、response_format={type:json_object}、thinking={type:disabled}，不返回思考内容。SDK maxRetries=0，关闭请求/响应日志。模型返回 length / refusal / 非 JSON / 缺失片段均视为失败，不应用半篇结果。

BoundedLangChainHttpClient 是 LangChain4j 的 HTTP SPI 适配器，仅保留原实现的传输保护：1 MiB 响应上限、覆盖完整响应体的截止时间、禁止重定向，以及清理后的错误消息。底层仍由 JDK HTTP 传输网络数据，但模型协议和调用流程由 LangChain4j 管理。

本地启动时在运行 content-service 的同一 pwsh 会话设置上述环境变量，再用原有开发启动脚本启动服务。Docker 演示则把配置添加到已有的、被忽略的 .runtime/docker-demo.env，仅 content-service 容器接收模型变量；修改配置后重新创建该容器。不要将真实密钥写入 .env.example 或提交版本库。

## 本地 HTTPS 握手故障排查

- `DeepSeek 安全连接失败` 对应 TLS/SSL 连接失败，不等于模型返回了不完整文章。先检查 content-service 日志中的异常类型链，再检查运行该 Java 进程的网络和 `DEEPSEEK_PROXY_URL`。
- S5 启动脚本会读取被忽略的 `.runtime/s5/local.env`。其中的模型和代理配置优先覆盖启动脚本所在终端的同名变量；修改文件后需要重启内容服务才能生效。
- 先用配置密钥请求官方 `/models` 检查连接、鉴权和模型名称；HTTP 200 只证明该次只读请求成功，不代表润色请求已经成功或网络持续稳定。
- `SSLHandshakeException` 可能来自网络/代理中断或证书验证失败。保留证书与主机名校验；不要用 trust-all、禁用 HTTPS 校验或自动重复模型请求来掩盖故障。
- Windows 下不要对正在运行的同路径 JAR 执行重新打包；应先停止对应服务，再构建并启动。若重新打包失败，不要使用 `-SkipBuild` 直接重启。

## 契约与边界

POST /api/v1/articles/ai-polish，需现有网关认证。输入和响应都是：

~~~json
{"segments":[{"id":"0.0","text":"一段正文"}]}
~~~

- 请求最多 200 个文本片段、合计 12000 个 UTF-16 字符。服务端验证 ID 唯一、长度、非空；响应必须准确覆盖同一组 ID。
- Redis 原子限流：每用户同一时刻一个请求、每 60 秒最多 6 次；Redis 不可用时不发起模型调用。活动锁带有界租约并按 token 释放。
- 只返回临时建议，不读写文章数据库、不推进草稿版本、不自动发布。不记录正文、完整模型错误或密钥。
- 原文在客户端保持快照；只有确认应用才进入现有编辑器更新、脏标记和保存流程。生成/预览/关闭不写入建议内容。
- 保留富文本结构、格式、图片、代码块、行内代码和链接；模型只接收可润色文本节点，结果也只能替换这些节点。标题输入框、摘要和封面不参与润色。
- 按钮触发时，可润色正文将被发送给配置的外部模型服务。运维方应按实际服务商确认数据留存政策；本实现不声称外部服务不留存。
- 原文在生成后发生变化时禁止应用旧结果；重新生成使用最新原文。关闭、中途切换文章或卸载会取消浏览器请求并丢弃晚到结果，但不能承诺已送达模型的任务停止计费。
- 应用为独立撤销历史项。确认应用前不会把建议写入自动保存。

网关为该 POST 路由单独设置 95 秒响应超时和 256 KiB 请求上限；Nginx 精确匹配该路径使用 100 秒，其余接口继续使用原有超时。模型响应最多接收 1 MiB，禁止跟随重定向。

## 展示

左侧 AI 按钮展开锚定气泡。容器内无标题、说明或操作文字；只有可滚动正文和右下角悬浮的重新生成、确认应用图标。生成时使用无文字骨架和图标状态；错误在外部 toast 提示。点击外部、再次点击入口或按 Escape 关闭；Escape 恢复入口焦点。支持 prefers-reduced-motion 和可视视口边界。

## 验证命令

~~~powershell
mvn -pl content-service,gateway-service -am test '-Dtest=ArticlePolish*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
~~~

前端：npm run test:s3-frontend 和 npm run build。

ArticlePolishHttpTest 使用本机临时 HTTP 模型夹具，验证真实 LangChain4j SDK 发出的 DeepSeek 兼容请求和 MVC/转发身份/校验/返回契约，不代表真实付费模型验证。前端浏览器回归同样应标注模型响应模拟边界。

### 2026-09-12 气泡功能基线验证记录（切换 SDK 前）

- 后端全 reactor 执行成功：242 个测试中 182 个通过、60 个依赖外部 MySQL/RabbitMQ 等环境的测试按配置跳过；0 failure / 0 error。新增润色相关 17 个测试全部执行并通过。
- 前端构建通过；S3 回归 29/29，认证会话回归 14/14。
- 真实编辑页面配合明确的本地模拟模型响应：纯正文、两个无可见文字的图标按钮、长文滚动、按钮悬浮位置、重新生成、应用、单步撤销、外部关闭、Escape 焦点恢复、取消后的晚到结果、过期原文保护均验证通过。
- 已检查开合过渡中间帧、1440px 桌面/390px 手机、明暗主题、减少动态效果设置；打开后入口收回悬停文字，气泡锚定图标中心，避免遮挡入口或左右漂移。
- 该基线使用测试响应，未调用真实付费模型，也未重新打包或部署 Docker 镜像。切换 SDK 后已提供 DeepSeek 地址和模型默认值，真实密钥仍需配置。

### 2026-09-12 LangChain4j + DeepSeek 切换验证

- 全 reactor 执行成功：247 个测试中 187 个通过、60 个环境相关测试跳过；0 failure / 0 error。
- 润色相关 22/22 全部执行通过：13 个 HTTP/配置契约用例、8 个服务/限流用例、1 个网关路由用例。
- 使用真实 LangChain4j SDK 对本地 HTTP 模型夹具发起请求，验证 deepseek-flash、JSON 输出、thinking=disabled、无自动重试、旧 endpoint 兼容、新旧环境变量优先级、/v1 路径、拒绝响应、响应大小和总超时保护。
- 解析后的依赖为 langchain4j-open-ai 1.20.0；项目 Jackson 仍为 2.15.4。未修改前端、网关接口契约或 Spring Boot/Java 基线。
- Docker Compose 配置解析与 git diff --check 通过。尚未使用真实 DeepSeek 密钥调用付费模型，未提交、推送或部署此次切换。
