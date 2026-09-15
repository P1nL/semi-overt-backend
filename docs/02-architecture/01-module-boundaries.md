# 模块边界

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

适合谁看：要理解仓库结构、服务职责和改动归属的人。  
读完能解决什么问题：知道每个模块该负责什么、不该负责什么，以及跨服务协作的边界在哪里。

## 先看结论

- 所有公网请求统一先进入 `gateway-service`
- 用户事实在 `auth-service`
- 文章事实在 `content-service`
- 审核命令/分配/日志在 `review-service`；文章决定结果以 Content 为准
- 搜索与通知是派生视图，不直接定义文章事实
- 共享能力放在 `platform-*`，跨服务调用契约放在 `*-contract`

## 业务服务边界

### `gateway-service`

负责：

- 外部路由分发
- 调用 Auth 权威接口校验设备会话，不维护独立 JWT 黑名单
- `X-Trace-Id` 生成 / 透传
- `X-User-Id`、`X-Username`、`X-User-Role` 注入
- Redis 限流
- 将登录、刷新、退出路由到 Auth 的 Cookie 会话流程

不负责：

- 业务数据真源
- 具体审核、内容、搜索逻辑

### `auth-service`

负责：

- 注册验证码、登录、刷新轮换、退出/撤销设备会话、找回密码、重置密码
- 持久化会话与跨实例请求预算
- 当前用户信息、用户主页资料
- 内部用户批量查询

不负责：

- 维护另一套绕过持久化设备会话的认证真源
- 文章生命周期

### `content-service`

负责：

- 首页、分类、文章详情
- 草稿创建与 version CAS 保存
- 正文 AI 润色建议（不直接保存文章）
- 提审、取消审核、删除文章
- 向审核和派生视图输出文章事实

它是文章状态真源。

### `review-service`

负责：

- 审核待办列表
- 审核命令、decisionId 幂等恢复与任务分配
- 审核日志
- 审核任务投影与最近审核结果查询

### `search-service`

负责：

- 公开搜索接口
- 消费文章状态变化事件，维护搜索可见结果

它只处理公开搜索视图，不是文章事实真源。

### `file-service`

负责：

- 图片格式、大小和解码校验；本地/所选对象存储上传
- 静态资源访问映射

### `notification-service`

负责：

- 消费文章状态变化事件
- 生成通知与通知投递记录
- 提供登录用户隔离的通知列表

## 支撑模块边界

### `platform-kernel`

放共享常量、结果模型、基础工具和跨服务都依赖的轻量类型，不承载 Spring Bean 编排。

### `platform-web-support`

放公共 Web 与安全支持，例如请求头认证过滤器、JWT 属性、Feign 支撑配置。

### `platform-events`

放 Outbox、RabbitMQ 拓扑、事件发布与通用消费执行器。事件基础设施统一沉淀在这里，而不是散落到各业务服务。

### `*-contract`

只放跨服务契约与 Feign client。

### `db-migration`

独立执行 Flyway 预检与迁移；不把已有库初始化等同于安全升级。

## 一条请求通常如何跨边界

公网请求：

`Client -> gateway-service -> 目标业务服务`

事件链路：

`content/review 真源 -> platform-events -> RabbitMQ -> notification/search/review/content 派生或回写`

## 改动时的边界判断

- 如果改动涉及“文章当前状态”，默认应先落在 `content-service`
- 如果改动涉及“审核动作是否合法”，默认应先落在 `review-service`
- 如果改动只影响“通知呈现”或“搜索展示”，通常不应回写内容真源
- 如果你想新增跨服务 HTTP 调用，优先检查是否应该通过 `*-contract` 暴露，而不是直接互相依赖实现模块
