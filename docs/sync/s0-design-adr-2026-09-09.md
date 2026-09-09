# S0 设计 ADR（2026-09-09）

> 状态：**Proposed / 待主代理裁决**。这些 ADR 是后续实现约束，不是目标代码已经具备的能力。
>
> 证据基线：前端真实 modules/adapters/authRuntime/http、单体源码、目标当前微服务源码。未读取生产，未部署，未升级依赖。

## ADR-001：共享 MySQL 的逻辑所有权

### 背景

第一阶段允许多个微服务使用同一 MySQL 实例，但不能因为物理共享就产生多个业务写入口。目标当前 `content-service` 直接调用 review client 删除任务，`review-service` 先写 review log/删任务再通过 outbox 通知 content；这只能说明现状，不能视为目标方案已经满足一致性。

### 决定

1. 第一阶段保留共享 MySQL，但按业务表建立**逻辑所有权**：
   - `auth-service`：`users`、设备会话/refresh token、验证码/重置记录。
   - `content-service`：`articles`、文章状态/版本/提交元数据、用户级首页曝光。
   - `review-service`：`review_tasks`、`review_logs`、审核命令幂等记录/结果投影。
   - `notification-service`：`notifications`、`notification_deliveries`。
   - `search-service`：只读共享查询或后续独立索引；在独立索引启用前不得成为文章写入口。
2. `platform-events` 是共享事件库/基础设施代码，不是独立运行服务，也不单独拥有业务表。`event_outbox` 与 `event_consume_log` 由各所属业务服务在本服务事务边界内按 producer/consumer 命名域写入：事件生产者与产生业务变更的服务共同写入对应 outbox；事件消费者与其业务副作用共同写入对应 consume log。共享库只提供模型、公共支持和幂等/发布基础能力，不能成为跨域事实写入口。
3. 共享预算/限流表属于基础设施数据域，不在 S0 指定由 `auth-service` 唯一写入。任何需要消耗预算的服务（例如 auth、file，以及后续明确的业务服务）不得各自直接定义并行写语义；S2 必须确定统一 owner/原子预算接口、表写入边界、跨实例一致性和故障策略后，才能将其列入正式所有权矩阵。
4. `content-service` 是文章状态的唯一写入者。审核服务不能直接更新 `articles`，不能以 review log 代替文章状态落地。
5. 跨域操作使用有版本/代次的内部命令或事件；禁止新增跨服务直接 JDBC 写入形成第二入口。
6. 共享 MySQL 的物理账号/权限隔离、迁移顺序和回收策略属于 S1/S6 实现与部署门槛；本 ADR 不声称当前数据库权限已隔离。

### 后果

- 共享实例降低第一阶段迁移复杂度，但事务边界仍是服务本地事务，不能宣称分布式事务。
- Outbox/Inbox、版本投影和幂等键仍是必需品，但其记录必须由相应 producer/consumer 业务服务在本地事务中写入；不能把共享库误解成独立服务或统一业务 owner。
- 共享预算的统一 owner、原子接口和权限边界延后到 S2 裁决；本 ADR 不预先授权 auth、file 等服务直接争用同一基础设施表。
- 独立分库/Elasticsearch 不纳入本 S0，也不因本 ADR 自动启用。

### 验收门槛

- 能列出每张业务表、每个事件 producer/consumer 命名域和共享预算 owner 的唯一写边界；S2 前共享预算 owner 可标记为待裁决，不得假设为 auth。
- 代码扫描无新增跨域写入；故障注入后文章、任务、日志能通过版本/幂等对账恢复。
## ADR-002：短 Access Token + durable device refresh

### 背景

当前前端 `authRuntime` 只把 access token 放在内存，Axios 使用 `withCredentials`，refresh 通过 HttpOnly Cookie；前端不再消费旧 `New-Token` 续期头。单体已有设备会话、refresh rotation、重放撤销、`rememberMe` Cookie 持久性语义。目标当前仍是 gateway 黑名单/长 JWT 旧模型。

### 决定

1. Access Token 为短生命周期 Bearer，仅在前端内存中保存；不把长期 access JWT 写入 localStorage。
2. 每个设备会话由服务端 durable session 记录，refresh token 只以 hash 持久化；refresh Cookie 为 HttpOnly、按环境设置 Secure/SameSite/Path。
3. refresh 必须轮换 token；旧 refresh token 被重放时撤销该 token family/设备会话，并返回 401；不能靠旧 `New-Token` 头替代 refresh。
4. `rememberMe=true` 允许持久 Cookie（有 Max-Age/Expires）；`rememberMe=false` 使用会话 Cookie（不设置持久化寿命）。不能用“关浏览器一定清除”作为跨浏览器证明。
5. logout 只读取当前 refresh Cookie，撤销该设备会话，重复/无 Cookie logout 幂等成功；不要求 Bearer，不依赖旧 JWT 黑名单作为最终会话模型。
6. 网络错误、超时、429、5xx 的 refresh 失败不得直接清除内存会话；只有明确 401 才能判定 refresh 会话失效。logout 失败保留 pending marker，并允许有界重试。

### 后果

- 网关、auth-service、Cookie 透传、Origin/CORS、前端跨标签锁必须一起验收。
- access TTL、idle refresh TTL、absolute refresh TTL、Cookie Domain/Path/SameSite 的最终数值仍需主代理在 S2 前裁决；S0 不臆造生产参数。
- 目标当前 `GatewayAuthController` 的 Authorization 黑名单 logout 只能作为旧实现证据，不能当作本 ADR 已落地。

### 验收门槛

- 登录、并发 refresh、refresh 重放、当前设备 logout、其他设备不受影响、密码重置全设备撤销均有可复核回执。
- Cookie-only logout 在无 Bearer 的真实浏览器请求中成功；Cookie 属性按持久/非持久分支断言。

## ADR-003：审核命令唯一决定，content 负责最终状态

### 背景

审核是跨服务流程。当前目标 `review-service` 的 `doReview` 会先写 review log、删除任务、写 `REVIEW_DECIDED` outbox；目标 content 通过事件再改变文章状态。网络/发布/消费窗口可能导致日志、任务和文章状态不一致。当前前端不认识“已入队即成功”。

### 决定

1. 每次用户审核动作绑定稳定 `decisionId`（后续可由 `Idempotency-Key` 承载）；同一 key 重试只能得到同一决定，不能创建第二次决定。
2. review-service 先校验分配人、作者排除、提交代次和任务状态，再持久化审核命令/claim。命令包含 `decisionId`、articleId、submissionId/提交序号、expected article version、operator、action、reason。
3. content-service 在本地事务中以 `PENDING + submissionId/代次 + expectedVersion + decisionId` 条件应用文章状态；唯一约束/结果表保证重复命令返回原结果。content 是唯一文章状态写入者。
4. 只有 content 的权威状态变更和审核结果记录成功后，review 才能写最终审核日志/关闭当前任务投影。通知只消费已确认的最终结果，通知投递失败不回滚文章决定。
5. 取消/删除只由 content 通过 CAS 推进文章状态；review 只能根据带代次/版本的事件更新投影。旧取消事件不得删除新提交任务。
6. Outbox 至少一次投递；消费者用 `eventId+consumer`、`decisionId` 和单调提交代次吸收重复、乱序、重启和 ack 前崩溃。随机 eventId 只去重，不负责排序。

### 后果

- 需要命令结果/幂等表、submissionId 或递增提交代次，以及事件版本字段。
- 旧复数审核路径可以兼容，但外部主路径固定 `/review/{id}/decision`。
- “写入 outbox”或“收到异步消息”不等于审核已通过/退回/拒绝。

### 验收门槛

- 两个管理员并发决定只允许一个最终结果。
- S1 提交→取消→S2 提交的乱序事件不能删除 S2 任务。
- 相同 decisionId 在超时后重试不会产生第二日志、第二状态推进或第二通知。

## ADR-004：审核对外使用有界同步接口；未知结果必须查询/同 key 重试

### 背景

当前前端 `POST /review/{id}/decision` 只消费最终的 `status` 与服务器时间。若后端返回“已接收/已入队”就当成成功，会把异步中间态错误地展示为最终审核结果。

### 决定

1. 在当前前端仍只理解成功结果的兼容期，decision POST 使用有界等待：只有确认 content 权威状态与结果记录后才返回 HTTP 200；原始返回至少 `{status,updatedAt,decisionId}`，前端再映射 `updatedAt -> reviewedAt`。
2. 到达服务端 deadline、连接中断或上游结果未知时，**不得返回最终成功**。服务端保留 decisionId 并使其可查询；调用方把结果标为 `UNKNOWN`，不自动生成新 key。
3. 后续可新增幂等查询接口，例如 `GET /review/{articleId}/decision-status?decisionId=...`：
   - `state=FINAL`：带同一最终 `status/updatedAt/decisionId`，才允许 UI 完成。
   - `state=PROCESSING`：仍是处理中，不是成功；按有界次数和退避查询。
   - command 未找到：使用**同一 decisionId**重试 POST；不能用新 key 规避不确定性。
4. 若未来采用 HTTP 202，响应必须明确 `PROCESSING` 并同时交付前端轮询/状态 UI；202 或“accepted/enqueued”绝不等同于审核成功。当前前端未改之前，不以 202 作为成功兼容方案。
5. 查询/重试策略固定为：同一 `decisionId`、有限重试次数、指数退避上限、最终转人工/错误状态；不得因超时盲目再次 POST 生成第二决定。
6. 文章状态最终成功与通知发送解耦：审核结果已由 content 确认即可结束同步接口；通知稍后幂等投递，不把通知延迟误报为审核未知，也不把消息收到误报为文章已变更。

### 后果

- 需要明确 deadline、查询保留期、PROCESSING 最大等待、重试次数与用户提示文案；这些数值由主代理在 S3 前裁决。
- 当前前端需要后续小改才能消费 decisionId/PROCESSING；S0 只冻结不得误报成功的边界。

### 验收门槛

- 人为制造“消息已发送但响应丢失”“content 已提交但 ack 丢失”“消费者重启”三种窗口，查询或同 key 重试均得到唯一最终结果。
- 任意 202、队列 ack、异步收到事件都不会直接使当前 UI 显示最终审核成功。

## ADR-005：草稿数据库持久化 + 显式 CAS；Redis 只能做可失效缓存

### 背景

当前单体 `DraftService` 以数据库文章为事实源并支持可选 expected version 的 CAS。目标旧 `DraftServiceImpl` 先写 Redis 草稿，再通过 `flushAllDrafts` 无版本更新数据库；Redis 丢失/批量 flush/并发覆盖都可能破坏草稿事实。

### 决定

1. `articles` 数据库记录是草稿唯一事实源；保存请求在本地数据库事务内更新正文/标题/摘要/封面与派生统计。Redis 如保留，只能缓存，丢失时不能丢业务数据。
2. 保存字段遵守当前 PATCH 语义：JSON `null` 表示不修改，空字符串表示用户明确清空；服务端重新计算 `wordCount/readMinutes/durationCategory`，不信任客户端派生值。
3. 仅 `DRAFT`、`RETURNED` 可编辑；保存不能恢复文章状态、清除审核元数据或改变提交计数；成功强制 `draftVisible=false`，除非另有明确公开草稿协议。
4. `version` 在兼容期可选：
   - 携带时：要求 `authorId/status/deleted/version` 同时匹配，条件更新并 `version+1`；不匹配返回 409。
   - 不携带时：服务端仍以本次读取的 version 做条件更新，但必须标注为兼容模式，不能宣称防止旧客户端/多标签陈旧覆盖。
5. 保存成功原始响应必须包含服务器 `updatedAt`；可同时返回当前 `version` 供未来前端接力。不得只返回 `savedAt`，不得以浏览器时间补齐服务器回执。
6. 禁止继续以无版本 Redis flush 作为事实写入；已有 Redis 草稿迁移需要显式数据策略、版本/时间界限和失败回执，属于 S1/S3。

### 后果

- 需要文章表拥有可用的 version、updatedAt、状态/删除条件；目标现有 V1 schema/entity 的 version 不完整是迁移缺口。
- 前端补齐编辑基线 version 是后续批准范围，不在 S0 偷改。

### 验收门槛

- 两个并发保存只有一个相同 version 能成功；陈旧写返回 409，不能覆盖状态或审核字段。
- Redis 断开时数据库保存仍可工作；缓存失效不造成数据丢失。
- 返回的时间来自数据库/服务端，且 raw JSON 为 `updatedAt`。

## 6. 主代理必须在后续阶段裁决的点

1. Access TTL、refresh idle/absolute TTL、Cookie Domain/Path/SameSite/Secure 的环境参数。
2. decision 有界等待 deadline、查询保留期、PROCESSING 轮询上限、重试次数和最终 UI 状态。
3. 是否在 S3 同时交付前端 decisionId/查询状态与 editor version；未交付前不得启用 202 或强制 version。
4. 共享 MySQL 的实际账号权限隔离方式，以及历史单体表/目标表的迁移映射。
5. 当前目标旧测试对“公开内容携带黑名单 token”的 401 预期是否改为公开匿名降级；S0 已冻结网站语义边界，但不代替主代理对测试基线的裁决。