# S0 外部契约矩阵（2026-09-09）

> 状态：**S0 冻结草案，待主代理审阅**。本文只冻结真实前端/单体行为与目标差异，不代表目标微服务已经实现；建议、S1/S3 实现项均明确标为未实现。
>
> 范围：仅契约、语义边界、错误/超时边界。未读取生产、未部署、未升级依赖。本文件不覆盖主代理的构建/测试 receipts。

## 1. 冻结原则

1. 当前前端实际网络调用优先于过时文档或生成类型；单体源码优先于目标旧微服务实现，用于冻结“当前网站语义”。
2. 前端默认 API base 为 `/api/v1`；部署可以覆盖为 `/api`。S0 要求两种前缀的外部路径语义一致。
3. 成功响应统一为 `{ "code": 200, "message": <string>, "data": <T> }`；错误不得用 HTTP 200 掩盖 401/403/404/409/429/5xx。
4. 日期必须是带 offset 的 ISO-8601 字符串。浏览器本地当前时间不能作为服务器保存/审核成功回执。
5. `updatedAt` 是当前保存/审核原始响应需要冻结的服务器字段；`savedAt`、`reviewedAt` 是当前前端模块/DTO 的归一化名称，不应反推为后端原始字段。
6. 当前前端保存请求**不传 `version`**。后端可兼容性地接受可选 `version`，但不能宣称当前前端已经提供陈旧写保护。
7. 外部路径冻结为单数 `/review`、`/upload`；复数旧路径只能作为兼容别名，不得让前端回退到复数主路径。

## 2. 端点与字段矩阵

以下路径均为相对于 `/api/v1` 或 `/api` 的路径。

| 领域 | 当前前端真实调用 | 原始请求/响应冻结 | 当前单体证据 | 目标当前差异 / S0 边界 |
|---|---|---|---|---|
| 注册验证码 | `POST /auth/register-code` | 请求 `email`, 可选 `cfTurnstileToken`；成功 `data: null` | `AuthController` 暴露该端点；验证码消费在 `AuthService` | 目标 `AuthController` 当前无该端点；S1 缺口，不在 S0 假设已完成 |
| 注册 | `POST /auth/register` | `email`, `username`, `password`, `emailCode`, `cfTurnstileToken`；成功返回 `token,userId,username,nickname,email,role,avatarUrl`；服务设置 refresh Cookie | 单体 `RegisterRequest` 与 `AuthController` | 目标 `RegisterReq` 当前缺 `emailCode`，目标没有设备 Cookie 流程；不得按目标现状写成已兼容 |
| 登录 | `POST /auth/login` | `account`, `password`, `rememberMe`；成功返回上述 Auth 数据；前端以 `withCredentials` 接收 Cookie | 单体 `LoginRequest`、`AuthController` | 目标有 `/api/v1/auth/login` 和长 JWT 旧模型；短 access + device refresh 仍是设计项 |
| 刷新 | `POST /auth/refresh` | 不依赖 Bearer；仅 HttpOnly refresh Cookie；成功仍返回可被 `normalizeAuthResp` 识别的 Auth 数据 | 单体从 Cookie 读取并轮换 `auth_refresh_tokens` | 目标当前没有对应设备刷新端点；不能用旧 `New-Token` 头冒充新协议 |
| 退出 | `POST /auth/logout` | **Cookie-only**：不要求 Bearer；撤销当前 Cookie 对应设备；无 Cookie/重复退出幂等成功，`data: null` | 单体 `AuthController.logout` 读取 Cookie；前端 `logoutDevice` 不附加 Authorization | 目标 `GatewayAuthController` 当前按 Authorization 黑名单处理；这是明确目标缺口，不能写成已实现 |
| 找回/重置密码 | `POST /auth/forgot-password`, `POST /auth/reset-password` | 找回请求 `email`；重置请求 `email,code,newPassword` | 单体两者均按验证码语义实现，并在重置后撤销全设备 | 目标重置 DTO 当前为 `token,newPassword`；字段协议不兼容 |
| 当前用户 | `GET /users/me` | 返回用户资料，包含 `id/userId,username,nickname,email,avatarUrl,coverUrl,signature,role,createdAt` 可供 adapter 归一化 | 单体 `UserProfileController` | 目标有 `/me`，响应/别名需由后续实现核对 |
| 更新资料 | `PUT /users/me` | `nickname,signature,avatarUrl,coverUrl`；`/users/me/profile` 是兼容别名 | 前端调用 `/me`；单体同时映射 `/me`、`/me/profile` | 目标当前只映射 `/me/profile`；S1 需兼容 `/me`，不在 S0 假设已完成 |
| 公共资料 | `GET /users/{identifier}/profile` | identifier 兼容用户名或数字 ID；分页 `tab,page,pageSize`（旧 `limit` 可兼容）；响应不向访客泄露 email，包含 stats/calendar/list 分页 | 单体按 ID 或 username 查找，访客只看 APPROVED；三年 `updated_at` 日历 | 目标当前方法名为 username；ID/隐私/日历完整语义仍待同步 |
| 创建文章 | `POST /articles` | 成功数据至少包含 `id,status=DRAFT` | 单体/目标均可返回文章或 `{id,status}`，前端只依赖创建 ID/状态 | 目标不得因返回完整实体而改变语义；草稿上限由服务端原子约束 |
| 保存草稿 | `PUT /articles/{id}/draft` | 请求：`title,content,summary,coverUrl,coverColor,clientWordCount`；当前前端不传 `version`。原始成功数据需含文章保存后的 `updatedAt,wordCount,readMinutes,durationCategory,status,draftVisible`，可额外含服务器 `version` | 单体 `SaveDraftRequest.version` 可选，CAS 更新并返回文章实体；当前前端 `article.ts` 读取 `article.updatedAt` | 目标 `SaveDraftReq` 当前没有 `version`，`SaveDraftResp` 当前为 `savedAt` 且缺 `draftVisible`；这是目标缺口。**不能只返回 `savedAt`**，也不能用浏览器 fallback 作为通过证据 |
| 草稿箱 | `GET /articles/drafts` | list 项至少含 `id,title,status,wordCount,updatedAt,latestReason,draftVisible`；状态集合 `DRAFT/PENDING/RETURNED/REJECTED` | 单体查询四态并提供最新原因 | 目标 `DraftServiceImpl` 当前只查 `DRAFT/RETURNED`，且通过 Redis 草稿 flush；需后续同步，不在 S0 宣称完成 |
| 文章详情 | `GET /articles/{id}` | 公共已发布文章可匿名读取；详情字段含 `updatedAt`、状态、作者、提交信息等；私有草稿/待审/退回/拒绝不得公开 | 单体 `ArticleService.getDetail` 仅 APPROVED 对匿名开放，其他状态要求作者或 ADMIN | **边界冻结**：公开内容带无效/过期 Bearer 时按匿名继续，只返回公开内容；私有资源不能因无效 Bearer 降级为公开，仍必须拒绝；受保护写接口的无效/缺失凭据必须拒绝 |
| 提审/取消/删除 | `POST /articles/{id}/submit`, `POST /articles/{id}/cancel-review`, `DELETE /articles/{id}` | 提审成功至少 `status,submitCount,lastSubmittedAt`；取消至少 `status`；删除成功 `data:null` | 单体有所有权、状态、文章版本 CAS；目标旧实现仍有 Redis 草稿和跨服务任务删除 | 状态推进与文章写入权归 content；审核任务通过事件/版本投影，不以 S0 现状为准 |
| 待审核列表 | `GET /review/pending` | 单数 `/review`；分页 `list,total,page,pageSize,pages`；项含 `id,title,submitCount,submittedAt,wordCount,author,assignedAdminId` | 单体 `/review/pending`；目标当前主路径为复数 `/reviews/pending` | 复数只保留兼容别名；结果应按分配管理员隔离，作者不能审核自己 |
| 审核决定 | `POST /review/{id}/decision` | 请求 `action=APPROVE|RETURN|REJECT`, 可选 `reason`；最终原始响应含 `status` 与服务器 `updatedAt`，前端再映射为 `reviewedAt` | 单体单体事务内校验分配人、文章版本并写日志/通知；`ReviewActionResponse` 内部用 `updatedAt` 作为 `reviewedAt` | 目标当前 `/reviews/{id}/action` 只属兼容旧路径；目标目前 review 先写日志/删任务再发事件，不能当作最终文章决定已落地 |
| 审核日志 | `GET /review/{id}/logs` | list 项 `action,fromStatus,toStatus,reason,operator,createdAt` | 单体与前端 adapter 字段一致 | 目标主路径需单数；日志只能代表已确认的最终决定，不能把“已入队”伪装成决定完成 |
| 文章搜索 | `GET /search` | `keyword,page,pageSize`；响应 `keyword,list,total,page,pageSize,pages`；仅公开 APPROVED 内容 | 单体同时支持根路径和 `/articles`，正文清洗/排序 | 目标当前只有 `/search/articles` 且 keyword 非空校验；需兼容根路径及当前前端空/分页语义 |
| 用户搜索 | `GET /search/users` | `keyword,page,pageSize`；响应分页用户摘要及 `profilePath` | 单体有总数/分页 | 目标已有同名端点，字段/上限仍需契约验收 |
| 图片上传 | `POST /upload` | multipart：`file,bizType,articleId?,oldUrl?`；返回 `url,width,height,size,dominantColor` | 单体同时兼容 `/upload`, `/uploads`, `/images` | 目标当前主路径为 `/uploads/images`；S0 主路径冻结为单数 `/upload`，旧复数仅别名；安全校验/oldUrl 清理另行实现 |
| 静态历史文件 | `GET /static/uploads/**` | 历史 local URL 保持可访问 | 单体 SecurityConfig 放行静态路径 | 目标 gateway/file 路由必须保留，不得因新存储方案删除旧 URL |

## 3. 关键字段冻结

### 3.1 保存与审核时间字段

- 保存草稿：当前 `src/shared/api/modules/article.ts` 第 42-50 行读取原始文章的 `updatedAt`，再在前端投影为本地 `savedAt`。后端原始响应的验收字段是 **`updatedAt`**。
- 审核决定：当前 `src/shared/api/modules/review.ts` 第 24-31 行读取原始响应的 `updatedAt`，再在前端投影为 `reviewedAt`。后端原始响应的验收字段仍是 **`updatedAt`**；生成类型中的 `reviewedAt` 不足以证明网络契约。
- 缺少服务器时间时，前端当前有 `new Date().toISOString()` fallback；该 fallback 只能说明客户端容错存在，不能作为后端契约通过。

### 3.2 version

- `SaveDraftReqDto` 与 `mapEditorFormToDraftPayload` 当前没有 `version`，所以 S0 fixture 的“当前前端保存请求”不得带 version。
- 目标设计可接受可选 `version`，并在存在时执行显式 CAS；未携带时只能做服务端本次读取后的 CAS，不能承诺多标签/旧编辑器的陈旧写保护。
- 未来补齐 version 需要同时更新详情 DTO、编辑器基线、保存请求和保存响应；这属于后续批准的同步增强，不是本文件已完成事项。

### 3.3 公开内容与无效 Token

- 公开文章详情（仅 APPROVED）和其他明确公开 GET：无 Token、过期 Token、黑名单 Token 都按匿名处理；不得把无效身份传成可信用户头。
- 私有文章详情、草稿箱、提审/取消/删除、审核决定、管理员列表：无 Token 或无效 Token 不得继续；已认证但权限不足返回 403，受保护且没有有效身份返回 401（具体私有详情的领域拒绝可表现为 403，但绝不能公开数据）。
- 这条边界解释当前网站方向，也解释目标旧测试 `GatewayAuthFilterTest.whitelistedRequestWithBlacklistedTokenReturnsUnauthorized` 与现有公开访问实现之间的冲突；该测试结论不在本 S0 文档中擅自改写。

## 4. 统一错误矩阵

| 情形 | HTTP / envelope | 说明 |
|---|---|---|
| 成功 | HTTP 200，`code=200` | `data` 可为 null；不得用成功包包装未完成异步命令 |
| 参数/验证码/正文校验失败 | 400 / `code=400` | 字段语义按当前前端请求冻结 |
| 缺失或无效受保护会话 | 401 / `code=401` | 自动刷新只对前端允许的 401 做一次；刷新网络失败不等于退出 |
| 已认证但无权限 | 403 / `code=403` | 角色、作者所有权、分配人校验失败 |
| 资源不存在 | 404 / `code=404` | 不用空成功数据掩盖 |
| 状态/CAS/幂等冲突 | 409 / `code=409` | 草稿陈旧写、文章非 PENDING、重复 key 参数不一致等 |
| 限流 | 429 / `code=429` + `Retry-After` | 预算必须跨实例共享；不能静默放行 |
| 服务/网络超时 | 5xx 或客户端网络错误 | 对审核命令结果只能标为“未知”，必须按同一幂等 key 查询/重试，不能当最终成功 |

## 5. 证据索引（本地快照）

### 前端

- `D:\works\semi-overt\src\shared\api\modules\auth.ts:14-75`
- `D:\works\semi-overt\src\shared\api\modules\article.ts:42-101`
- `D:\works\semi-overt\src\shared\api\modules\review.ts:18-35`
- `D:\works\semi-overt\src\shared\api\modules\user.ts:22-37`
- `D:\works\semi-overt\src\shared\api\modules\search.ts:18-43`
- `D:\works\semi-overt\src\shared\api\modules\upload.ts:4-39`
- `D:\works\semi-overt\src\shared\api\adapters.ts:20-188,228-317,393-476`
- `D:\works\semi-overt\src\shared\api\authRuntime.ts:243-281,386-477`
- `D:\works\semi-overt\src\shared\api\http.ts:146-203`
- `D:\works\semi-overt\src\shared\api\generated\contracts.ts:61-248`
- `D:\works\semi-overt\src\features\article-editor\model\editor.mapper.ts:59-102`

### 单体来源

- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\auth\api\AuthController.java:21-144`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\auth\repository\JdbcDeviceSessionRepository.java:46-177`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\auth\api\user\UserProfileController.java:29-109`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\content\api\ArticleController.java:30-131`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\content\service\DraftService.java:45-99`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\review\api\ReviewController.java:21-62`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\review\api\ReviewV1Controller.java:20-59`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\config\SecurityConfig.java:52-91`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\auth\security\JwtAuthenticationFilter.java:40-81`
- `D:\works\semi-overt-springboot\src\main\java\com\platform\semiovert\config\ApiDateTimeConfig.java:15-34`

### 目标当前实现（仅用于差异标记）

- `D:\works\semi-overt-backend\gateway-service\src\main\java\com\platform\gateway\config\GatewayRouteConfig.java:26-57`
- `D:\works\semi-overt-backend\gateway-service\src\main\java\com\platform\gateway\filter\GatewayAuthFilter.java:38-180`
- `D:\works\semi-overt-backend\gateway-service\src\main\java\com\platform\gateway\controller\GatewayAuthController.java:30-45`
- `D:\works\semi-overt-backend\auth-service\src\main\java\com\platform\auth\controller\AuthController.java:17-57`
- `D:\works\semi-overt-backend\auth-service\src\main\java\com\platform\auth\controller\UserController.java:23-57`
- `D:\works\semi-overt-backend\content-service\src\main\java\com\platform\content\controller\ArticleController.java:20-77`
- `D:\works\semi-overt-backend\content-service\src\main\java\com\platform\content\api\req\SaveDraftReq.java:9-27`
- `D:\works\semi-overt-backend\content-service\src\main\java\com\platform\content\api\resp\SaveDraftResp.java:16-23`
- `D:\works\semi-overt-backend\review-service\src\main\java\com\platform\review\controller\ReviewController.java:19-49`
- `D:\works\semi-overt-backend\review-service\src\main\java\com\platform\review\service\impl\ReviewServiceImpl.java:143-190`
- `D:\works\semi-overt-backend\review-service\src\main\java\com\platform\review\service\impl\ReviewTaskServiceImpl.java:23-69`
- `D:\works\semi-overt-backend\content-service\src\main\java\com\platform\content\service\impl\DraftServiceImpl.java:50-193`
- `D:\works\semi-overt-backend\db\migration\V1__baseline_schema.sql:18-131`

## 6. 不属于 S0 的事项

- 不修改业务源码、测试、根计划或其他仓库。
- 不把目标当前路径/DTO/测试现状写成已兼容。
- 不运行构建、部署、生产检查、迁移或依赖升级。
- 不进入 S1；S0 退出只表示契约/ADR/fixture 已落盘并待审阅。