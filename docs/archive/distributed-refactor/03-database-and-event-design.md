# 数据库与事件模型设计文档

## 1. 当前数据模型回顾
当前单体项目的核心数据模型非常集中，主要包含以下三张业务表：

### `users`
用途：

- 存储用户账号、邮箱、密码、角色、头像、签名等信息

当前业务归属：

- 注册、登录、找回密码、用户主页、当前用户信息

### `articles`
用途：

- 存储文章元数据、正文、封面、字数、阅读时长、状态、提交次数等信息

当前业务归属：

- 草稿创建
- 草稿保存
- 提交审核
- 文章详情
- 首页与分类聚合

### `review_logs`
用途：

- 存储管理员审核动作、状态变更和原因

当前业务归属：

- 待审核处理后的记录留痕
- 作者与管理员查看审核日志
- 文章详情读取最近退回/拒绝原因

当前 Redis 参与的真实业务状态有：

- JWT 黑名单
- 找回密码 token 与发送限流
- 草稿正文缓存
- 首页 Hero 缓存

当前项目没有事件表、通知表、文件资产表和审核任务表，这些都是分布式改造后新增的支撑模型。

## 2. 目标数据归属原则
分布式改造后，数据库仍先共用一个 MySQL schema，但数据归属必须清晰：

- `auth-service`：`users`
- `content-service`：`articles`
- `review-service`：`review_logs`、`review_tasks`
- `notification-service`：`notifications`、`notification_deliveries`
- `file-service`：`file_assets`
- 跨服务基础支撑：`event_outbox`、`event_consume_log`

约束原则：

1. 表只能有一个服务负责最终写入。
2. 其他服务不得直接跨服务操作他人表。
3. 跨服务查询优先走内部接口，跨服务状态传播优先走事件。
4. 派生数据失败不回滚主状态。

## 3. 各服务表设计
### 3.1 `auth-service`
负责表：

- `users`

职责说明：

- 作为用户身份和资料的唯一真源
- 对外提供登录注册与用户资料接口
- 对内提供用户批量查询接口

### 3.2 `content-service`
负责表：

- `articles`

职责说明：

- 作为文章状态、文章详情和草稿元数据的唯一真源
- 持有与草稿正文有关的 Redis Key：`draft:{userId}:{articleId}`
- 只允许 `content-service` 最终写入文章状态

### 3.3 `review-service`
负责表：

- `review_logs`
- `review_tasks`

职责说明：

- `review_logs` 负责审核留痕
- `review_tasks` 负责待审核工作队列投影

### 3.4 `notification-service`
负责表：

- `notifications`
- `notification_deliveries`

职责说明：

- 站内信主表
- 各投递渠道发送结果表

### 3.5 `file-service`
负责表：

- `file_assets`

职责说明：

- 文件元数据统一归档
- 对应当前上传服务返回的 URL、尺寸、主色等信息

### 3.6 跨服务基础支撑
负责表：

- `event_outbox`
- `event_consume_log`

职责说明：

- 保障事件可投递
- 保障消费者幂等

## 4. 新增表设计
### `review_tasks`
表用途：

- 保存待审核任务投影，支持管理员分页查看和处理队列

关键字段：

- `id`
- `article_id`
- `author_id`
- `status`
- `submit_count`
- `submitted_at`
- `last_event_id`
- `created_at`
- `updated_at`

所属服务：

- `review-service`

与现有表关系：

- 由 `articles` 的提交审核事件投影而来
- 与 `articles.id` 一一对应

### `notifications`
表用途：

- 存储站内通知主记录

关键字段：

- `id`
- `user_id`
- `type`
- `title`
- `content`
- `biz_id`
- `read_status`
- `created_at`

所属服务：

- `notification-service`

与现有表关系：

- `user_id` 对应 `users.id`
- `biz_id` 可关联文章 ID 或审核业务 ID

### `notification_deliveries`
表用途：

- 记录邮件、站内信等通知投递明细和重试情况

关键字段：

- `id`
- `notification_id`
- `channel`
- `status`
- `retry_count`
- `last_error`
- `sent_at`
- `created_at`

所属服务：

- `notification-service`

与现有表关系：

- 关联 `notifications.id`

### `file_assets`
表用途：

- 存储上传文件的元数据，便于后续替换本地文件系统或接入对象存储

关键字段：

- `id`
- `owner_id`
- `biz_type`
- `related_id`
- `url`
- `mime_type`
- `size`
- `width`
- `height`
- `dominant_color`
- `created_at`

所属服务：

- `file-service`

与现有表关系：

- `owner_id` 对应 `users.id`
- `related_id` 可关联文章或用户资料

### `event_outbox`
表用途：

- 记录本地事务成功后待发布的事件，解决“业务写库成功但 MQ 发送失败”问题

关键字段：

- `event_id`
- `aggregate_type`
- `aggregate_id`
- `event_type`
- `payload`
- `status`
- `next_retry_at`
- `retry_count`
- `created_at`

所属服务：

- 由产生事件的服务各自写入

与现有表关系：

- 与 `articles`、`review_logs`、`notifications` 等业务表形成事务性配合

### `event_consume_log`
表用途：

- 记录消费者是否已处理某个事件，保证幂等

关键字段：

- `id`
- `event_id`
- `consumer`
- `status`
- `consumed_at`
- `error_message`

所属服务：

- 各事件消费者所在服务

与现有表关系：

- 不直接关联业务表，作为事件消费幂等日志

## 5. 事件模型设计
本次改造固定定义三类核心事件。

### `ArticleSubmittedEvent`
触发方：

- `content-service`

消费方：

- `review-service`

字段列表：

- `eventId`
- `traceId`
- `articleId`
- `authorId`
- `submitCount`
- `submittedAt`

触发时机：

- 用户提交草稿审核，`articles.status` 成功更新为 `PENDING` 后触发

幂等键：

- `eventId`

用途说明：

- 驱动审核任务投影生成或刷新

### `ReviewDecidedEvent`
触发方：

- `review-service`

消费方：

- `content-service`

字段列表：

- `eventId`
- `traceId`
- `articleId`
- `adminId`
- `action`
- `reason`
- `reviewedAt`

触发时机：

- 管理员完成审核动作并写入 `review_logs` 后触发

幂等键：

- `eventId`

用途说明：

- 通知 `content-service` 应用文章状态变更

### `ArticleStatusChangedEvent`
触发方：

- `content-service`

消费方：

- `notification-service`
- `search-service`

字段列表：

- `eventId`
- `traceId`
- `articleId`
- `authorId`
- `fromStatus`
- `toStatus`
- `title`
- `summary`
- `publishedAt`

触发时机：

- `content-service` 成功应用审核结果并更新 `articles` 状态后触发

幂等键：

- `eventId`

用途说明：

- 驱动通知发送
- 驱动 Elasticsearch 索引更新或删除

## 6. 队列与死信设计
RabbitMQ 队列设计固定如下：

### 主队列
- `article.submitted`
- `review.decided`
- `article.status.changed`

### 重试队列
- `article.submitted.retry`
- `review.decided.retry`
- `article.status.changed.retry`

### 死信队列
- `article.submitted.dlq`
- `review.decided.dlq`
- `article.status.changed.dlq`

设计原则：

- 消费失败先进入重试队列，使用 TTL 延迟重新投递
- 重试次数超限后进入死信队列
- 死信队列用于人工排查和后续补偿

## 7. 幂等设计
幂等策略固定如下：

1. 生产侧幂等
- 每个事件在业务事务中生成唯一 `eventId`
- 同一事务只允许写入一条有效 outbox 记录

2. 消费侧幂等
- 每个消费者在处理前先检查 `event_consume_log`
- 唯一键按 `eventId + consumer`
- 已成功消费则直接跳过

3. 业务侧幂等
- `review_tasks` 需要按 `article_id` 去重更新
- 搜索索引更新按 `articleId` 幂等覆盖
- 通知投递记录按 `notification_id + channel` 去重

## 8. 最终一致性方案
本次方案明确采用最终一致性，不做强一致分布式事务。

### 核心机制
1. 本地事务写业务表
2. 同事务写 `event_outbox`
3. 后台任务或消息发布器扫描 outbox 并发送 MQ
4. 消费者写 `event_consume_log`
5. 消费失败时重试，超限后入死信

### 关键约束
- `content-service` 是文章状态唯一真源
- `review-service` 不直接修改文章最终状态
- `search-service` 和 `notification-service` 失败不回滚文章主状态
- 失败补偿通过重试、死信、人工修复完成

### 对当前项目的意义
这样既保留了现有文章状态机和审核日志模型，又能把通知、搜索这些后续扩展能力安全地挂在主链路后面，不会把主业务事务拖成一串同步调用。

## 9. 拆库演进路径
本次改造第一阶段不执行物理拆库，但要提前按服务归属约束代码。

推荐拆库顺序：

1. `auth-service`
- 用户域最独立，拆库成本最低

2. `review-service`
- 审核任务和审核日志天然独立，适合第二个拆库

3. `notification-service`
- 通知属于派生业务，拆库对主链路影响最小

4. `file-service`
- 文件元数据独立性强，适合后续接对象存储时一起迁移

5. `content-service`
- 文章表是最核心数据，最后拆库，避免早期把系统复杂度拉得过高

拆库前提：

- 所有跨服务读取都已经切换为内部接口
- 所有跨服务状态传播都已经切换为事件
- 不再存在跨服务直接联表和跨服务事务写入
