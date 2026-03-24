# 服务拆分与迁移实施文档

## 1. 拆分原则
本次服务拆分遵循以下固定原则：

1. 先拆服务，后拆库。
第一阶段允许多个服务共享同一个 MySQL schema，但从代码边界上禁止跨服务直查数据表。

2. 对外接口尽量兼容。
外部入口继续使用 `/api/v1/**`，由 `gateway-service` 统一对外提供，降低前端改造成本。

3. 单一真源原则。
每类核心数据只能有一个服务负责最终写入：

- 用户数据真源在 `auth-service`
- 文章状态真源在 `content-service`
- 审核日志真源在 `review-service`

4. 事件驱动优先补链，不强求强一致。
通知、搜索、审核任务等派生能力优先通过 MQ 解耦，不引入 Seata。

5. 先做最小可讲链路。
优先完成 `gateway/auth/content/review`，再补 `notification/search/file`。

## 2. 当前模块到目标服务映射
当前单体项目已经存在较清晰的业务模块，本次拆分采用如下映射：

| 当前包结构/模块 | 目标服务 | 说明 |
| --- | --- | --- |
| `controller/auth + service/auth + mapper/user` | `auth-service` | 注册、登录、登出、找回密码、用户资料 |
| `controller/article/home/category + service/article/draft/home/category + mapper/article` | `content-service` | 首页、分类、文章、草稿 |
| `controller/review + service/review + mapper/review_log` | `review-service` | 待审核、审核动作、审核日志 |
| `controller/upload + service/upload` | `file-service` | 图片上传与元数据 |
| `controller/search + service/search` | `search-service` | 搜索服务，替换现有占位实现 |
| 网关与统一鉴权能力 | `gateway-service` | 统一入口、鉴权、路由 |
| 审核后通知相关新增能力 | `notification-service` | 站内信与邮件通知 |

## 3. 各服务职责边界
### 3.1 `gateway-service`
负责的现有模块：

- 当前单体中的 `JwtAuthFilter`、部分 `SecurityConfig` 路由规则、统一请求拦截逻辑

负责的数据库表：

- 无业务表

暴露的外部接口：

- `/api/v1/auth/**`
- `/api/v1/users/**`
- `/api/v1/home`
- `/api/v1/categories/**`
- `/api/v1/articles/**`
- `/api/v1/reviews/**`
- `/api/v1/search/**`
- `/api/v1/uploads/**`

暴露的内部接口：

- 无内部业务接口

依赖的其他服务：

- `auth-service`
- `content-service`
- `review-service`
- `notification-service`
- `search-service`
- `file-service`
- Redis（黑名单校验）
- Nacos（注册与配置）

边界说明：

- 负责 JWT 解析、黑名单校验、TraceId 注入、路由转发
- 不承载业务规则，不直接连接业务数据库
- 向下游统一注入请求头：
  - `X-User-Id`
  - `X-Username`
  - `X-User-Role`
  - `X-Trace-Id`

### 3.2 `auth-service`
负责的现有模块：

- `AuthController`
- `UserController`
- `AuthServiceImpl`
- `UserServiceImpl`
- `UserDetailsServiceImpl`
- `UserMapper`

负责的数据库表：

- `users`

暴露的外部接口：

- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/auth/logout`
- `/api/v1/auth/forgot-password`
- `/api/v1/auth/reset-password`
- `/api/v1/users/me`
- `/api/v1/users/me/profile`
- `/api/v1/users/{username}/profile`

暴露的内部接口：

- `POST /internal/users/batch`

依赖的其他服务：

- Redis
- 邮件组件
- Nacos

边界说明：

- 负责用户身份生命周期与用户资料真源
- 提供用户摘要查询给 `content-service`、`review-service`
- 不负责文章、审核、通知、搜索逻辑

### 3.3 `content-service`
负责的现有模块：

- `ArticleController`
- `HomeController`
- `CategoryController`
- `ArticleServiceImpl`
- `DraftServiceImpl`
- `HomeServiceImpl`
- `CategoryServiceImpl`
- `ArticleMapper`

负责的数据库表：

- `articles`

暴露的外部接口：

- `/api/v1/home`
- `/api/v1/categories/{category}/articles`
- `/api/v1/articles`
- `/api/v1/articles/drafts`
- `/api/v1/articles/{articleId}`
- `/api/v1/articles/{articleId}/draft`
- `/api/v1/articles/{articleId}/submit`
- `/api/v1/articles/{articleId}/cancel-review`
- `/api/v1/articles/{articleId}`

暴露的内部接口：

- `GET /internal/articles/{id}/review-snapshot`
- `POST /internal/articles/{id}/apply-review-result`

依赖的其他服务：

- `auth-service`
- `review-service`
- Redis
- RabbitMQ
- Nacos

边界说明：

- 负责文章与草稿的唯一真源
- 负责首页与分类页聚合
- 草稿 Redis Key 只归 `content-service` 所有
- 不直接写审核日志，不直接发通知，不直接写搜索索引

### 3.4 `review-service`
负责的现有模块：

- `ReviewController`
- `ReviewServiceImpl`
- `ReviewLogMapper`

负责的数据库表：

- `review_logs`
- `review_tasks`

暴露的外部接口：

- `/api/v1/reviews/pending`
- `/api/v1/reviews/{articleId}/action`
- `/api/v1/reviews/{articleId}/logs`

暴露的内部接口：

- `GET /internal/reviews/articles/{id}/latest`

依赖的其他服务：

- `content-service`
- `auth-service`
- RabbitMQ
- Nacos

边界说明：

- 负责审核动作、审核日志和审核任务投影
- 不直接成为文章状态真源
- 审核结果通过事件交给 `content-service` 应用

### 3.5 `notification-service`
负责的现有模块：

- 当前项目无对应独立模块，本次新增

负责的数据库表：

- `notifications`
- `notification_deliveries`

暴露的外部接口：

- 第一阶段不提供对外业务接口
- 后续可扩展 `/api/v1/notifications/**`

暴露的内部接口：

- 当前设计中不强依赖同步内部接口，以事件消费为主

依赖的其他服务：

- RabbitMQ
- `auth-service`
- Nacos

边界说明：

- 负责审核通过、退回、拒绝后的站内信与邮件通知
- 不参与文章主状态事务

### 3.6 `search-service`
负责的现有模块：

- `SearchController`
- `SearchServiceImpl`

负责的数据库表：

- 无主业务表，可选维护索引同步日志表

暴露的外部接口：

- `/api/v1/search/articles`

暴露的内部接口：

- 当前设计不要求同步内部接口，以事件消费为主

依赖的其他服务：

- RabbitMQ
- Elasticsearch
- Nacos

边界说明：

- 替换当前搜索占位实现
- 仅索引 `APPROVED` 内容
- 不回写文章主状态

### 3.7 `file-service`
负责的现有模块：

- `UploadController`
- `UploadServiceImpl`

负责的数据库表：

- `file_assets`

暴露的外部接口：

- `/api/v1/uploads/images`

暴露的内部接口：

- 当前阶段可不暴露

依赖的其他服务：

- Nacos
- 本地文件系统或对象存储

边界说明：

- 负责图片校验、落盘、尺寸提取、主色提取
- 对外返回 URL 与文件元数据
- 与内容服务之间通过 URL/文件 ID 解耦

## 4. 外部 API 路由映射
分布式改造后，外部路径继续保持兼容，由 `gateway-service` 统一路由：

| 外部路径 | 目标服务 |
| --- | --- |
| `/api/v1/auth/**` | `auth-service` |
| `/api/v1/users/**` | `auth-service` |
| `/api/v1/home` | `content-service` |
| `/api/v1/categories/**` | `content-service` |
| `/api/v1/articles/**` | `content-service` |
| `/api/v1/reviews/**` | `review-service` |
| `/api/v1/search/**` | `search-service` |
| `/api/v1/uploads/**` | `file-service` |

兼容性原则：

- 路径尽量不变
- 主要返回结构尽量不变
- 业务语义不变
- 真正变化的是请求经过网关和服务内部调用链

## 5. 内部接口设计
为避免拆分后继续跨库直查，本次内部接口固定为以下四个：

### `POST /internal/users/batch`
由 `auth-service` 提供，用于根据用户 ID 批量查询用户摘要信息。

调用方：

- `content-service`
- `review-service`

### `GET /internal/articles/{id}/review-snapshot`
由 `content-service` 提供，用于向审核服务返回文章快照。

调用方：

- `review-service`

用于替代拆分前 `review-service` 直接访问 `articles` 表的行为。

### `POST /internal/articles/{id}/apply-review-result`
由 `content-service` 提供，用于应用审核结果。

调用方：

- `review-service` 的异步消费者或同步补偿逻辑

### `GET /internal/reviews/articles/{id}/latest`
由 `review-service` 提供，用于查询文章最新退回/拒绝原因。

调用方：

- `content-service`

用于替代当前文章详情中直接查 `review_logs` 的方式。

## 6. 服务间依赖关系
服务依赖尽量保持单向、清晰、可解释：

- `gateway-service` 依赖所有下游服务进行路由转发
- `content-service` 依赖 `auth-service` 获取作者信息
- `content-service` 依赖 `review-service` 获取最新审核原因
- `review-service` 依赖 `content-service` 获取审核快照和应用审核结果
- `notification-service` 主要依赖 MQ，必要时依赖 `auth-service` 获取投递所需用户信息
- `search-service` 主要依赖 MQ 和 Elasticsearch
- `file-service` 与其他服务尽量保持弱依赖

依赖控制原则：

- 尽量避免循环同步调用
- 非关键链路优先改为事件消费
- 聚合展示类接口放在业务真源侧完成，不在网关拼装

## 7. 阶段一迁移清单
阶段一固定为：`gateway/auth/content/review/common`

实施内容：

- 将项目改造为 Maven 父工程 + 多模块
- 提取公共模块，放置统一 DTO、错误码、通用头读取工具、事件模型基础类
- 新增 `gateway-service`
- 从单体中拆出 `auth-service`
- 从单体中拆出 `content-service`
- 从单体中拆出 `review-service`
- 接入 `Nacos + OpenFeign`
- 改造同步数据获取方式，去掉跨模块直接查表

阶段一交付结果：

- 原有核心流程仍可跑通
- 对外 API 基本兼容
- 网关成为唯一公网入口

## 8. 阶段二迁移清单
阶段二固定为：`notification/search + MQ`

实施内容：

- 接入 RabbitMQ
- 新增 `event_outbox`
- 新增 `event_consume_log`
- 提交审核和审核结果改为事件驱动
- 新增 `notification-service`
- 新增 `search-service`
- 将当前搜索占位实现替换为 Elasticsearch 真实检索
- 新增 `review_tasks` 审核任务投影

阶段二交付结果：

- 审核链路形成完整异步闭环
- 审核通过后可通知、可搜索
- 可以演示重试、幂等、死信和最终一致性

## 9. 阶段三迁移清单
阶段三固定为：`file + docker-compose + 可观测性`

实施内容：

- 拆出 `file-service`
- 上传逻辑从内容服务彻底移除
- 增加本地基础设施编排，如 `docker-compose`
- 增加日志规范、TraceId 透传、健康检查、运行文档

阶段三交付结果：

- 项目结构更完整
- 本地演示门槛更低
- 更适合面试展示和后续扩展

## 10. 风险点与迁移注意事项
### 风险 1：`content-service` 当前依赖 `users/review_logs` 的同步查询
当前文章详情、首页、审核日志展示等逻辑会直接依赖用户或审核表。拆分后必须改为内部接口：

- 用户信息走 `POST /internal/users/batch`
- 最新审核原因走 `GET /internal/reviews/articles/{id}/latest`

### 风险 2：草稿正文仍在 Redis
当前草稿正文保存在 `draft:{userId}:{articleId}` 中。拆分后这个 Key 的归属必须只属于 `content-service`，其他服务不能读写该缓存。

### 风险 3：网关统一鉴权后，下游服务不再重复解析 JWT
改造过程中最容易出现的错误是网关已经完成用户认证，但下游服务还保留旧的 JWT 过滤器和安全上下文依赖。迁移时必须统一收口到“网关解析 JWT，下游读取请求头”。

### 风险 4：审核服务不要直接写文章主状态
如果 `review-service` 直接更新 `articles`，就会破坏“文章状态真源在 `content-service`”的设计，后续补偿和幂等都会变复杂。

### 风险 5：搜索与通知失败不能影响主状态
审核通过后，主状态更新成功就应视为核心事务完成。搜索和通知属于派生链路，失败后只能重试或补偿，不能回滚文章状态。
