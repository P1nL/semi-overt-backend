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
- `POST /internal/articles/profile-page`
- `GET /internal/reviews/articles/{id}/latest`

内容域线程已固定的事实如下，后续线程不得回退：

- `content-service` 已不再跨表直查 `users` / `review_logs`
- `content-service` 已不再本地写 `review_logs`
- `articles.status` 最终只能由 `content-service` 写入
- `POST /api/v1/articles/{articleId}/cancel-review` 当前语义固定为：`content-service` 只负责 `PENDING -> DRAFT` 状态回退，不负责本地补写 `CANCEL` 审核日志
- 如需恢复“取消审核也有审核日志”这条业务语义，必须由后续审核域线程或事件线程负责把日志真源收口回 `review-service`
- 草稿 Redis Key `draft:{userId}:{articleId}` 只归 `content-service`
- 草稿保存会同步刷新 `articles.content` 持久化快照；提交审核成功后会删除对应 draft key
- 用户主页聚合已固定为 `auth-service -> POST /internal/articles/profile-page -> content-service`，viewer 身份只能来自网关透传的 `X-User-*`

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

### 3.1 认证与网关线程补充说明
- 公开接口的最终鉴权语义已经固定，不得在后续线程中改回“白名单即完全跳过鉴权”：
  - 无 `Authorization`：匿名放行
  - 有效 token：继续透传 `X-User-Id`、`X-Username`、`X-User-Role`
  - 失效、解析失败或黑名单 token：直接返回兼容 `401`
- 上述规则尤其适用于 `GET /api/v1/articles/{id}` 和 `GET /api/v1/users/{username}/profile` 这类“公开但仍需身份感知”的接口，后续线程需要在此语义上继续设计，不得破坏作者查看草稿/待审核详情等场景。
- 本机联调时，优先使用 `MAVEN_CMD` 指向真实 `mvn.cmd`；不要默认信任 `mvnw.cmd` 在所有机器上都稳定可用。当前 `dev-up.ps1` 已支持优先读取 `MAVEN_CMD`。
- 当前这台开发机已确认可用的 Maven 路径示例为：`C:\Users\PINKING\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd`
- 认证与网关线程的回归重点应显式覆盖：
  - 登录作者点击草稿箱中的草稿，不再跳 `404`
  - 登出后旧 token 访问公开文章详情，必须返回 `401`，不能静默降级为匿名
  - 单模块启动不再出现 `Unknown lifecycle phase ".run.profiles=local"`

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

### 4.1 内容域线程补充说明
- 内容域线程必须建立在网关既定语义之上：`GET /api/v1/articles/{id}` 虽然是公开接口，但不能等同于“完全匿名接口”。
- 对文章详情的正确理解是：
  - 无 `Authorization`：按匿名视角返回，只允许公开内容
  - 有效 token：下游能拿到 `X-User-*`，因此作者本人可以读取自己的草稿、退回稿、待审核稿详情
  - 失效、解析失败或黑名单 token：网关直接返回 `401`
- 内容域线程不得用“把私有稿件改成公开可见”这种方式规避鉴权问题，必须继续依赖网关注入的用户头做作者视角权限判断。
- 内容域线程必须接受以下新增事实已经固定，不得在后续设计中回退：
  - `content-service` 除既有内部接口外，还必须提供 `POST /internal/articles/profile-page`
  - 该接口专门服务 `auth-service` 的 `GET /api/v1/users/{username}/profile` 聚合，`auth-service` 不再直查 `articles` / `review_logs`
  - viewer 身份只能来自网关透传的 `X-User-Id`、`X-Username`、`X-User-Role`
  - `content-service` 内部自行判定 `isSelf || isAdmin`，不得让调用方传 `canViewAll` 一类权限参数
  - 用户主页文章聚合必须继续复用 `GET /internal/reviews/articles/{id}/latest` 获取最新退回/拒绝原因
- 用户主页与文章详情继续共用“公开但身份感知”语义：匿名可访问公开内容，有效 token 透传身份，失效或黑名单 token 由网关直接返回真实 HTTP `401`。
- 内容域线程的回归重点应显式覆盖：
  - 登录作者点击草稿箱中的草稿，不再跳 `404`
  - 匿名访问公开文章详情仍然正常
  - 旧 token 访问文章详情时返回 `401`，而不是静默降级为匿名
  - `GET /api/v1/users/{username}/profile` 对本人或管理员可见非 `APPROVED` 状态，对他人只能看到 `APPROVED`
  - `POST /internal/articles/profile-page` 不得成为绕过身份判断的后门接口

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
5. 设计“取消审核(CANCEL)日志”如何重新收口到 review-service
6. 为后续 MQ 异步审核链路做好事件接口准备

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
7. 必须接受以下新增事实已经固定，不得回退：
   - `content-service` 已不再本地写 `review_logs`
   - `POST /api/v1/articles/{articleId}/cancel-review` 当前只做文章状态回退
   - 如需保留 `CANCEL` 审核日志，必须由 `review-service` 作为日志真源承接

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

### 5.1 审核域线程补充说明
- 审核域线程必须建立在已落地的认证与内容聚合边界之上：`review-service` 不得解析 JWT，身份恢复继续只基于 `HeaderAuthenticationFilter`。
- 对外鉴权失败语义已经固定为真实 HTTP `401/403`，不得回退为 HTTP `200 + code` 的兼容包装模式。
- `review-service` 继续只依赖固定 4 个内部头和既定内部接口边界，不得新增身份头或额外的内部鉴权协议。
- `review-service` 需要继续保证 `GET /internal/reviews/articles/{id}/latest` 可被 `content-service` 用于用户主页文章聚合，不得因为 `review_tasks`、outbox 或日志重构而破坏该用途。
- 后续若调整审核日志查询或审核投影实现，不得破坏 `content-service -> review-service -> auth-service` 这条既定依赖方向。
- 审核域线程必须显式处理 `CANCEL` 语义的归属问题：内容域已不再本地补写 `CANCEL` 日志，因此后续若要恢复该留痕，只能由审核域自己定义同步补写或等待事件线程定义异步补写方案。
- 但无论采用同步内部接口还是异步事件，`review_logs` 的最终真源都必须仍在 `review-service`，不得把 `CANCEL` 日志写回 `content-service`。
- 审核域线程的回归重点应显式覆盖：
  - 普通用户访问审核接口时返回真实 HTTP `403`
  - 审核原因查询兼容用户主页聚合场景，不因 `review_tasks` / outbox 改造而中断
  - 若恢复 `CANCEL` 审核日志，该日志必须由 `review-service` 生成且不破坏 `PENDING -> DRAFT` 既有外部语义

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
8. 必须接受以下新增事实已经固定，不得回退：
   - `content-service` 已不再本地写 `review_logs`
   - `cancel-review` 当前只完成文章状态回退；如要恢复 `CANCEL` 留痕，必须通过事件链路把日志写回 `review-service`

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

### 6.1 事件与派生能力线程补充说明
- 事件线程必须建立在已落地的同步认证链路之上：认证模型仍固定为“gateway 校验 JWT + auth-service 签发 JWT + 下游只认头”，不得在事件线程中重新引入服务内 token 校验。
- 搜索、通知、投影都属于派生能力，不得反向成为文章状态、用户资料或鉴权结果的真源。
- 事件线程改造后不得破坏 `GET /api/v1/articles/{id}`、`GET /api/v1/users/{username}/profile` 的身份感知语义；失效、解析失败或黑名单 token 仍必须由网关在入口处返回真实 HTTP `401`。
- 不得把 `logout`、JWT 黑名单、token 解析回迁到 MQ 消费服务或任何业务服务。
- 后续搜索结果、通知内容、投影补数若需要用户摘要，仍应走 `POST /internal/users/batch`，不得重新跨服务直查用户真源表。
- 若事件线程承担“取消审核补日志”职责，必须明确：
  - 触发方仍是 `content-service`
  - `review-service` 才是 `review_logs` 的最终写入方
  - 该链路失败不能回滚文章主状态，只能按最终一致性补偿
- `ArticleStatusChangedEvent` 若覆盖 `PENDING -> DRAFT` 的取消审核场景，必须保留足够字段让 `review-service` 判断是否需要生成 `CANCEL` 留痕，但不得让消费方反向改写 `articles.status`
- 事件与派生能力线程的回归重点应显式覆盖：
  - 异步链路失败不影响公开接口的既定鉴权语义
  - 搜索或通知改造后，公开文章详情和公开主页的身份感知行为保持不变
  - 若引入取消审核补日志链路，`CANCEL` 留痕最终落在 `review-service`，且文章状态仍只由 `content-service` 写

### 6.2 已落地审核域事实
- `review-service` 已不再本地直连 `articles`；原审核服务内的 `Article` 实体、`ArticleMapper`、`ArticleMapper.xml` 已移除。
- `review-service` 当前已收口为只负责：
  - `review_logs`
  - `review_tasks`
- `GET /api/v1/reviews/pending` 已改为只查 `review_tasks`，排序固定为 `submitted_at DESC, article_id DESC`，并继续排除管理员审核自己提交的文章。
- `review_tasks` 当前字段语义已固定包含：
  - `article_id`
  - `author_id`
  - `title`
  - `word_count`
  - `status`
  - `submit_count`
  - `submitted_at`
  - `last_event_id`
- 在 MQ / outbox 尚未落地前，`content-service` 已通过同步内部接口维护审核任务投影：
  - `POST /internal/reviews/tasks/upsert`
  - `POST /internal/reviews/tasks/remove`
- 当前同步审核链路已实际落地为：
  - 提审成功后，`content-service` 同步 upsert `review_tasks`
  - 取消审核成功后，`content-service` 同步 remove `review_tasks`
  - 管理员审核时，`review-service` 先写 `review_logs`、删 `review_tasks`，再同步调用 `POST /internal/articles/{id}/apply-review-result`
- 公共层已新增 `ReviewDecisionPayload`，字段固定为：
  - `articleId`
  - `adminId`
  - `action`
  - `reason`
  - `reviewedAt`
  - `fromStatus`
  - `toStatus`
  - `traceId`
- 后续事件线程若落地 `ReviewDecidedEvent`，应优先复用 `ReviewDecisionPayload` 的字段语义，不要再定义另一套审核决定载荷。
- `GET /internal/reviews/articles/{id}/latest` 仍然只从 `review_logs` 中查询最新 `RETURN/REJECT` 原因；后续改造不得让它依赖 `review_tasks`、通知、搜索或其他派生表。
- `CANCEL` 留痕目前仍未恢复，且同步链路没有新增补日志接口；这件事已固定留给后续事件线程通过 `ArticleStatusChangedEvent(PENDING -> DRAFT)` 异步补写到 `review-service`。
- 对外鉴权失败语义已实际改为真实 HTTP `401/403`；后续线程不得再退回到 HTTP `200 + code` 的兼容包装模式。

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

### 7.1 交付与运维线程补充说明
- 本机启动方案必须把 `MAVEN_CMD` 作为一等入口，优先允许显式指定真实 `mvn.cmd`；不要默认信任 `mvnw.cmd` 在所有 Windows 机器上都稳定可用。
- 当前这台开发机的已知可用示例路径为：`C:\Users\PINKING\.m2\wrapper\dists\apache-maven-3.9.12-bin\5nmfsn99br87k5d4ajlekdq10k\apache-maven-3.9.12\bin\mvn.cmd`
- 本地开发入口与服务器运行入口已经分层固定：
  - Windows 本地开发继续使用 `scripts/dev-up.ps1` / `scripts/dev-up.cmd`
  - 上述脚本只用于本地开发，不是服务器运行入口
  - Linux 服务器基线入口固定为 `scripts/run-service.sh <service-name> [profile] [env-file]`
  - 服务器运行形态固定为 `java -jar target/<service>-1.0.0.jar`，不再把 `spring-boot:run` 当成云上运行方式
- 所有服务都已统一暴露 `GET /actuator/health` 和 `GET /actuator/info`，运维线程必须在这个基线上继续补运行说明和检查项，而不是重新定义健康检查入口。
- 云上配置基线已经固定为“环境变量 + Nacos”，示例样板在 `scripts/env/server.env.example`；后续线程不得再默认依赖本地 profile 文件或本地路径。
- `content-service` 当前默认单实例运行，因为仍存在定时任务且尚未做分布式协调；交付说明必须显式保留这条运行限制。
- 上云前置文档已经固定在 `docs/distributed-refactor/06-cloud-readiness-baseline.md`，后续交付与运维线程必须在该基线上增量推进，而不是从 Docker / K8s 反推当前运行模型。
- 启动脚本和运行说明需要明确：
  - `MAVEN_CMD` 指向的 `mvn.cmd` 优先通过 PowerShell 直接调用，并使用参数数组传递参数，不再额外套 `cmd.exe /d /c call ...`
  - 如需在受限环境重定向 Maven settings 或本地仓库，统一通过 `MAVEN_SETTINGS`、`MAVEN_REPO_LOCAL` 注入，不要在脚本外再包一层 `cmd.exe`
  - `-Dspring-boot.run.profiles=local`、`-Dmaven.repo.local=...` 这类参数必须作为单个参数传递，不能再被拆坏
  - 服务是否“已启动”必须以目标端口是否真实监听为准，不能只依赖残留 PID
- 运维线程的回归重点应显式覆盖：
  - 单模块启动不再出现 `Unknown lifecycle phase ".run.profiles=local"`
  - 服务启动失败后再次执行启动脚本，不会因为残留 launcher PID 被误判为“已运行”
  - `gateway-service` 重启后，公开接口仍满足“无 token 匿名放行、有效 token 透传身份、坏 token 返回 `401`”这条既定语义
  - Linux `run-service.sh` 方式启动后，各服务 `/actuator/health`、`/actuator/info` 可访问
  - 运行说明明确区分“本地开发脚本”“Linux 服务器启动脚本”“后续正式部署方案”三层职责

## 8. 验收要求
所有新线程都必须满足以下共性要求：

- 必须引用这 3 份设计文档作为唯一权威设计入口
- 必须重复固定约束：`先拆服务后拆库`、`外部 API 兼容`、`不引入 Seata`
- 不得在线程内改写已固定的内部接口边界
- 默认只有当现有代码事实与设计文档直接冲突时，线程才允许提出修订建议
- 后续线程默认建立在已落地的认证模型之上：网关统一鉴权、下游只认头、鉴权失败返回真实 HTTP `401/403`
- 后续线程默认建立在已落地的 Linux 运行基线之上：`java -jar`、`run-service.sh`、`/actuator/health`、`/actuator/info`
- 后续线程只能做增量收敛，不得把已落地的 `POST /internal/articles/profile-page`、`run-service.sh`、`scripts/env/server.env.example`、`06-cloud-readiness-baseline.md` 视为不存在
- 后续线程必须接受：`content-service` 不再本地写 `review_logs`；如需补齐 `CANCEL` 留痕，只能由审核域或事件链路把日志真源收口回 `review-service`
- 若线程涉及启动/回归说明，Windows 本地优先使用 `MAVEN_CMD`，Linux 运行优先使用打包产物，不混用两套入口

## 9. 默认假设
- 默认所有新线程都在同一个仓库上下文内工作
- 默认新线程目标是“按既定方案落地”，不是重新讨论架构方向
- 默认线程产物应当足够具体，能直接指导编码与实施
