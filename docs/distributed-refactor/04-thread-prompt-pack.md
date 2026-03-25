# 分布式改造新线程提示词包

## 1. 使用说明
本文件用于在新线程中快速拉起分布式改造的具体子任务。所有线程都必须严格遵循当前项目已经确认的改造路线，不得在线程内重新发散架构方向。

统一约束如下：

- 先拆服务，后拆库
- 外部 API 保持 `/api/v1/**` 兼容
- 不引入 Seata，不做强一致分布式事务
- 文章状态真源在 `content-service`
- 所有线程都必须引用以下 3 份设计文档作为唯一权威入口：
  - `E:\code\now-demo\docs\distributed-refactor\01-overall-distributed-design.md`
  - `E:\code\now-demo\docs\distributed-refactor\02-service-split-and-migration-plan.md`
  - `E:\code\now-demo\docs\distributed-refactor\03-database-and-event-design.md`

固定内部接口边界如下，不允许线程内自行改写：

- `POST /internal/users/batch`
- `GET /internal/articles/{id}/review-snapshot`
- `POST /internal/articles/{id}/apply-review-result`
- `GET /internal/reviews/articles/{id}/latest`

推荐使用顺序：

1. 基础设施线程
2. 认证与网关线程
3. 内容域线程
4. 审核域线程
5. 事件与派生能力线程
6. 交付与运维线程

## 2. 基础设施线程提示词
```text
你现在负责这个项目的“基础设施线程”，目标是为后续分布式改造提供统一骨架，而不是直接实现所有业务服务。

项目背景：
- 当前项目是一个 Spring Boot 单体内容平台
- 已有能力包括：注册登录、草稿、文章提交审核、审核日志、首页聚合、分类、上传、搜索占位
- 现有核心表：users、articles、review_logs
- Redis 当前用途：jwt:blacklist、pwd:reset、pwd:reset:lock、draft:{userId}:{articleId}、home:hero:{yyyy-MM-dd}
- 当前搜索仍是占位实现，后续要接入 Elasticsearch

本线程目标：
1. 将当前单体工程规划为 Maven 父工程 + 多模块结构
2. 设计并落地基础公共模块 common
3. 设计 gateway-service 的基本职责边界
4. 设计 Nacos、OpenFeign、统一配置、统一异常、统一请求头协议
5. 为后续 auth/content/review 服务拆分打基础

必须遵守：
1. 先拆服务，后拆库
2. 外部 API 继续保持 /api/v1/** 兼容
3. 不引入 Seata，不做强一致分布式事务
4. 下游服务最终不再重复解析 JWT，而是信任网关注入的请求头
5. 内部统一请求头固定为：
   - X-User-Id
   - X-Username
   - X-User-Role
   - X-Trace-Id

参考文档：
- E:\code\now-demo\docs\distributed-refactor\01-overall-distributed-design.md
- E:\code\now-demo\docs\distributed-refactor\02-service-split-and-migration-plan.md
- E:\code\now-demo\docs\distributed-refactor\03-database-and-event-design.md

你需要输出：
1. 建议的多模块目录结构
2. common 模块应承载的内容清单
3. gateway-service 的路由与鉴权方案
4. Nacos、Feign、配置分层方案
5. 这一线程的实施顺序
6. 风险点与边界说明

验收标准：
- 后续线程可以在不重做骨架的前提下直接拆 auth/content/review
- 不留下“模块怎么拆、公共类放哪、统一请求头怎么传”这类空白决策
- 结果必须足够具体，能直接指导编码
```

## 3. 认证与网关线程提示词
```text
你现在负责这个项目的“认证与网关线程”，目标是把现有认证体系从单体安全模型迁移到“网关统一鉴权 + auth-service 真源”的分布式模型。

项目背景：
- 当前单体使用 Spring Security + JWT
- 当前有 AuthController、UserController、AuthServiceImpl、UserServiceImpl、UserDetailsServiceImpl、JwtAuthFilter、SecurityUtils
- users 表是用户数据真源
- Redis 当前承担 JWT 黑名单和找回密码 token/限流
- 目标架构中 auth-service 负责注册、登录、登出、找回密码、用户资料、公开主页
- gateway-service 负责 JWT 校验、黑名单校验、请求头注入、路由转发
- 当前基础设施线程已经落地多模块骨架，现有模块包括：
  - common
  - gateway-service
  - auth-service
  - content-service
  - review-service
  - search-service
  - file-service
  - notification-service
- common 已经提供：
  - Result / GlobalExceptionHandler / BusinessException
  - HeaderNames / ServiceNames
  - HeaderAuthenticationFilter
  - FeignHeaderRelayInterceptor / FeignCommonConfig
  - JwtProperties
  - BatchUserQueryReq / UserSummaryDto 等内部 DTO
- gateway-service 已经有：
  - 路由骨架
  - JWT 校验骨架
  - Redis 黑名单校验骨架
  - X-User-* / X-Trace-Id 注入与覆盖逻辑
  - /api/v1/auth/logout 的网关本地处理入口
- auth-service 已经有：
  - 启动类、基础安全配置、application.yml
  - POST /internal/users/batch 骨架
- 本线程必须基于这些现有骨架继续收敛，不得重建另一套认证模型

本线程目标：
1. 从单体中拆出 auth-service
2. 将 JWT 解析职责从业务服务收口到 gateway-service
3. 固定 auth-service 对外接口和对内接口
4. 设计并迁移用户上下文传递方式
5. 保证现有 /api/v1/auth/** 和 /api/v1/users/** 的对外兼容性

必须遵守：
1. 外部接口保持兼容：
   - /api/v1/auth/register
   - /api/v1/auth/login
   - /api/v1/auth/logout
   - /api/v1/auth/forgot-password
   - /api/v1/auth/reset-password
   - /api/v1/users/me
   - /api/v1/users/me/profile
   - /api/v1/users/{username}/profile
2. 用户数据真源只能在 auth-service
3. 网关统一解析 JWT，下游服务不再重复解析
4. 内部请求头必须使用：
   - X-User-Id
   - X-Username
   - X-User-Role
   - X-Trace-Id
5. auth-service 必须暴露内部接口：
   - POST /internal/users/batch
6. logout 的 Token 黑名单写入必须继续收口在 gateway-service，不得回退到 auth-service 再解析 JWT
7. 下游服务身份恢复必须继续基于 common 中的 HeaderAuthenticationFilter，不得重新引入服务内 JwtAuthFilter
8. 不得改写已固定的 Nacos 命名约定：
   - group = NOW_DEMO
   - 服务名使用 spring.application.name
9. 不得新增内部身份头字段，内部头协议只能保留 4 个固定字段

参考文档：
- E:\code\now-demo\docs\distributed-refactor\01-overall-distributed-design.md
- E:\code\now-demo\docs\distributed-refactor\02-service-split-and-migration-plan.md
- E:\code\now-demo\docs\distributed-refactor\03-database-and-event-design.md
- E:\code\now-demo\docs\distributed-refactor\05-infra-skeleton-implementation.md

你需要输出：
1. auth-service 的职责边界与代码迁移清单
2. gateway-service 中认证相关职责的实现方案
3. 从 JwtAuthFilter/SecurityUtils 迁移到网关注入头的方案
4. auth-service 的外部接口与内部接口设计
5. Redis 黑名单与找回密码能力在分布式下的保留方式
6. 测试与回归清单
7. 对当前已落地骨架的增量修改清单，而不是从零重建清单

验收标准：
- 登录、登出、找回密码、用户资料流程在分布式下仍成立
- 其他服务不再需要自己解析 JWT
- 后续 content/review 线程可以直接依赖 auth-service 的内部用户接口
- 不会破坏 gateway-service 已有的请求头覆盖、防伪造、logout 本地收口这三条边界
- 不会把认证公共类重新散落回各服务，认证相关公共能力继续收敛在 common
```

## 4. 内容域线程提示词
```text
你现在负责这个项目的“内容域线程”，目标是将现有文章、草稿、首页、分类相关能力拆到 content-service，并明确它作为文章状态真源的边界。

项目背景：
- 当前单体中 ArticleController、HomeController、CategoryController、ArticleServiceImpl、DraftServiceImpl、HomeServiceImpl、CategoryServiceImpl、ArticleMapper 都属于内容域
- articles 是内容域核心表
- 草稿正文当前存 Redis：draft:{userId}:{articleId}
- 文章状态流转为：DRAFT -> PENDING -> APPROVED/RETURNED/REJECTED，且支持 PENDING -> DRAFT 的取消审核
- 当前 content 逻辑会同步依赖 users 和 review_logs 的查询

本线程目标：
1. 将文章、草稿、首页、分类拆到 content-service
2. 保持 content-service 作为 articles 的唯一真源
3. 设计与 auth-service、review-service 的内部接口依赖
4. 去掉 content-service 对 users/review_logs 的跨表直查
5. 保留现有草稿、提交审核、撤回审核、首页聚合的业务语义

必须遵守：
1. 外部接口保持兼容：
   - /api/v1/home
   - /api/v1/categories/{category}/articles
   - /api/v1/articles
   - /api/v1/articles/drafts
   - /api/v1/articles/{articleId}
   - /api/v1/articles/{articleId}/draft
   - /api/v1/articles/{articleId}/submit
   - /api/v1/articles/{articleId}/cancel-review
   - /api/v1/articles/{articleId} [DELETE]
2. articles 的最终状态只能由 content-service 写入
3. 草稿 Redis Key 只归 content-service 所有
4. 内容服务查询用户摘要必须走：
   - POST /internal/users/batch
5. 内容服务查询最新审核原因必须走：
   - GET /internal/reviews/articles/{id}/latest
6. 内容服务必须提供内部接口：
   - GET /internal/articles/{id}/review-snapshot
   - POST /internal/articles/{id}/apply-review-result

参考文档：
- E:\code\now-demo\docs\distributed-refactor\01-overall-distributed-design.md
- E:\code\now-demo\docs\distributed-refactor\02-service-split-and-migration-plan.md
- E:\code\now-demo\docs\distributed-refactor\03-database-and-event-design.md

你需要输出：
1. content-service 的职责边界与代码迁移清单
2. 草稿与文章状态的真源定义
3. 首页、分类、详情、草稿、提交审核相关接口迁移方案
4. 对 auth-service/review-service 的内部接口依赖设计
5. 草稿缓存和首页缓存的分布式保留策略
6. 测试与回归清单

验收标准：
- 不再跨表直查 users/review_logs
- 文章状态机在 content-service 内依旧清晰可控
- 后续 review 和 MQ 线程能直接挂接到 content-service
```

## 5. 审核域线程提示词
```text
你现在负责这个项目的“审核域线程”，目标是把现有管理员审核能力拆到 review-service，并把审核结果从“直接改文章”过渡到“审核日志真源 + 状态结果输出”。

项目背景：
- 当前单体中的 ReviewController、ReviewServiceImpl、ReviewLogMapper 属于审核域
- review_logs 是现有审核留痕表
- 当前审核流程里，管理员可以查看待审核列表、执行 APPROVE/RETURN/REJECT、查看审核日志
- 当前审核逻辑和文章状态变更耦合较深
- 改造后 review-service 还要新增 review_tasks 用于待审核任务投影

本线程目标：
1. 将待审核列表、审核动作、审核日志拆到 review-service
2. 设计 review_tasks，支撑分页待审核队列
3. 保持 review_logs 作为审核留痕真源
4. 让审核服务不再成为文章状态真源
5. 为后续 MQ 异步审核链路做好事件接口准备

必须遵守：
1. 外部接口保持兼容：
   - /api/v1/reviews/pending
   - /api/v1/reviews/{articleId}/action
   - /api/v1/reviews/{articleId}/logs
2. review-service 负责表：
   - review_logs
   - review_tasks
3. review-service 不直接成为文章状态真源
4. review-service 需要依赖：
   - GET /internal/articles/{id}/review-snapshot
   - POST /internal/articles/{id}/apply-review-result
   - POST /internal/users/batch
5. 管理员不能审核自己提交的文章
6. RETURN/REJECT 必须保留 reason 语义

参考文档：
- E:\code\now-demo\docs\distributed-refactor\01-overall-distributed-design.md
- E:\code\now-demo\docs\distributed-refactor\02-service-split-and-migration-plan.md
- E:\code\now-demo\docs\distributed-refactor\03-database-and-event-design.md

你需要输出：
1. review-service 的职责边界与迁移清单
2. review_tasks 的设计与待审核列表生成策略
3. review_logs 与文章状态解耦方案
4. 管理端审核动作的内部调用与后续事件化准备
5. 风险点与一致性边界
6. 测试与回归清单

验收标准：
- 审核动作、审核日志、待审核列表都可由 review-service 独立承接
- 审核服务与内容服务的边界清晰
- 后续可以无缝接入 ReviewDecidedEvent
```

## 6. 事件与派生能力线程提示词
```text
你现在负责这个项目的“事件与派生能力线程”，目标是为分布式改造补齐 RabbitMQ、event_outbox、event_consume_log，并新增 notification-service 与 search-service，打通“提交 -> 审核 -> 通知 -> 搜索”的异步链路。

项目背景：
- 当前搜索仍是占位实现，SearchServiceImpl 返回空结果
- 当前没有 MQ、没有事件表、没有通知表、没有审核任务投影表
- 设计文档已经固定三类事件：
  - ArticleSubmittedEvent
  - ReviewDecidedEvent
  - ArticleStatusChangedEvent
- 设计文档已经固定新增表：
  - review_tasks
  - notifications
  - notification_deliveries
  - event_outbox
  - event_consume_log

本线程目标：
1. 设计 RabbitMQ 主队列、重试队列、死信队列
2. 设计并落地 event_outbox 与 event_consume_log
3. 将提审与审核结果改造成事件驱动
4. 新增 notification-service
5. 新增 search-service 并接入 Elasticsearch
6. 固定幂等、重试、死信和最终一致性策略

必须遵守：
1. 事件定义固定为：
   - ArticleSubmittedEvent
   - ReviewDecidedEvent
   - ArticleStatusChangedEvent
2. RabbitMQ 队列必须明确：
   - 主队列
   - 重试队列
   - 死信队列
3. 消费幂等按 eventId + consumer
4. 必须使用：
   - event_outbox
   - event_consume_log
5. 搜索与通知失败不回滚文章主状态
6. search-service 仅索引 APPROVED 文章
7. notification-service 负责站内信与邮件通知

参考文档：
- E:\code\now-demo\docs\distributed-refactor\01-overall-distributed-design.md
- E:\code\now-demo\docs\distributed-refactor\02-service-split-and-migration-plan.md
- E:\code\now-demo\docs\distributed-refactor\03-database-and-event-design.md

你需要输出：
1. MQ 拓扑设计
2. 三类事件的发布方、消费方、字段、触发时机、幂等键
3. outbox/consume_log 的落地方案
4. notification-service 的职责与表设计落地方式
5. search-service 的索引同步策略与搜索接口替换方案
6. 异步链路的失败补偿、重试、死信处理策略
7. 测试与回归清单

验收标准：
- 形成完整异步链路：提交 -> 审核 -> 状态回写 -> 通知 -> 搜索
- 不依赖强一致分布式事务
- 搜索和通知作为派生数据处理，不影响文章主状态真源
```

## 7. 交付与运维线程提示词
```text
你现在负责这个项目的“交付与运维线程”，目标是让分布式改造后的项目具备本地演示、联调、运行说明和基础可观测性，而不是只停留在服务拆分层面。

项目背景：
- 当前项目是单体结构，后续会拆成 gateway/auth/content/review/notification/search/file
- 本地未来需要依赖 MySQL、Redis、Nacos、RabbitMQ、Elasticsearch
- 目标是做成可用于面试展示的成品项目，而不是只完成服务代码拆分

本线程目标：
1. 设计本地一键启动方案
2. 设计 docker-compose 基础设施编排
3. 设计基础日志规范与 TraceId 透传规范
4. 设计健康检查、运行说明、联调说明
5. 设计最小集成测试与端到端验收方案

必须遵守：
1. 本地基础设施至少覆盖：
   - MySQL
   - Redis
   - Nacos
   - RabbitMQ
   - Elasticsearch
2. TraceId 统一通过：
   - X-Trace-Id
3. 必须明确每个服务的启动顺序、端口规划、配置来源
4. 必须提供面向演示的启动说明和验证链路
5. 不能把可观测性设计成重型生产系统，保持面试项目可实现性

参考文档：
- E:\code\now-demo\docs\distributed-refactor\01-overall-distributed-design.md
- E:\code\now-demo\docs\distributed-refactor\02-service-split-and-migration-plan.md
- E:\code\now-demo\docs\distributed-refactor\03-database-and-event-design.md

你需要输出：
1. docker-compose 规划
2. 服务端口与配置约定
3. 本地启动顺序
4. 日志与 TraceId 规范
5. 健康检查与最小监控项
6. 集成测试与端到端验收场景
7. 面试演示建议流程

验收标准：
- 新同学拿到仓库后能快速在本地起服务
- 至少能演示一条完整链路：注册登录 -> 提交审核 -> 审核通过 -> 收到通知 -> 搜索可见
- 交付方式足够工程化，但不过度复杂
```

## 8. 验收要求
所有新线程都必须满足以下共性要求：

- 必须引用这 3 份设计文档作为唯一权威设计入口
- 必须重复固定约束：`先拆服务后拆库`、`外部 API 兼容`、`不引入 Seata`
- 不得在线程内改写已固定的内部接口边界
- 默认只有当现有代码事实与设计文档直接冲突时，线程才允许提出修订建议

## 9. 默认假设
- 默认所有新线程都在同一个仓库上下文内工作
- 默认新线程目标是“按既定方案落地”，不是重新讨论架构方向
- 默认线程产物应当足够具体，能直接指导编码与实施
