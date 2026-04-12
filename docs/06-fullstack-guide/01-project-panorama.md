# 项目全景：技术栈、模块关系与阅读路径

这份文档是你认识这个项目的第一站。不需要动手写代码，先把"地图"看懂，知道有哪些模块、各自负责什么、它们之间怎么协作，后面深入任何一块都会省力很多。

---

## 第一部分：技术栈

项目分前端和后端两个代码库，另外还有几个中间件通过 Docker Compose 统一管理。

### 前端技术栈（`C:\Users\PINKING\WebstormProjects\now`）

| 技术 | 在项目里负责什么 | 重要程度 |
|---|---|---|
| Vue 3 (Composition API) | 前端框架核心，所有组件和页面都基于它 | ★★★ |
| Vue Router 5 | 前端路由管理，处理页面跳转、权限守卫、Sheet 弹层路由 | ★★★ |
| Pinia 3 | 全局状态管理，包含 auth / ui / editor / draft / session 等多个 store | ★★★ |
| TanStack Vue Query 5 | 服务端数据的缓存、自动刷新和失败重试，替代手写 loading 状态 | ★★★ |
| Axios | HTTP 请求客户端，内置拦截器负责 token 注入和 token 刷新 | ★★★ |
| Tailwind CSS v4 | 原子化 CSS 框架，配合 CSS 自定义属性实现亮/暗主题切换 | ★★★ |
| TypeScript 5 | 全项目类型安全保障，减少运行时错误 | ★★★ |
| Vite 7 | 开发构建工具，负责代理后端请求、HMR 热更新和生产打包 | ★★ |
| Tiptap 3 (ProseMirror) | 富文本编辑器，用于文章内容的创作和编辑 | ★★ |
| @vueuse/core | Vue 组合式工具库，提供大量响应式工具函数 | ★★ |
| Headless UI | 无样式但具备可访问性的基础组件（弹窗、下拉等） | ★★ |
| @vueuse/motion | Vue 动画库，处理进入/离开过渡动效 | ★ |
| GSAP | 高级动画引擎，用于复杂的时间轴动画 | ★ |
| Lucide Vue | 图标库 | ★ |
| highlight.js + markdown-it | 代码块高亮和 Markdown 内容渲染 | ★ |
| DOMPurify | 渲染富文本前做 HTML 内容安全清洗，防 XSS | ★ |
| dayjs | 轻量日期时间处理库 | ★ |
| openapi-typescript | 从 OpenAPI 规范文件自动生成 TypeScript 类型定义 | ★ |

### 后端技术栈（`E:\code\now-demo`）

| 技术 | 在项目里负责什么 | 重要程度 |
|---|---|---|
| Java 17 | 后端开发语言 | ★★★ |
| Spring Boot 3.2.3 | 应用框架核心，各微服务的基础骨架 | ★★★ |
| Spring Cloud Gateway | API 网关，统一接收前端请求，做路由分发、JWT 校验和限流 | ★★★ |
| Spring Cloud OpenFeign | 微服务之间的 HTTP 调用，用接口声明代替手写 HTTP 客户端 | ★★★ |
| Spring Security | 安全框架，提供身份认证过滤器链 | ★★★ |
| MyBatis Plus | ORM 框架，简化数据库的增删改查操作 | ★★★ |
| MySQL 8 | 关系型数据库，存储所有业务数据 | ★★★ |
| Redis 7 | 缓存层，同时也用于 JWT 黑名单和密码重置 token 存储 | ★★★ |
| RabbitMQ 3 | 消息队列，驱动服务间的异步通信（如审核通过后触发通知） | ★★★ |
| Nacos 2 | 服务注册发现 + 配置中心，各服务启动时向它注册并拉取配置 | ★★ |
| JJWT 0.12 | JWT token 的签发和验签 | ★★ |
| Lombok | 注解生成 getter/setter/Builder 等，减少 Java 样板代码 | ★★ |
| Flyway (db-migration 模块) | 数据库版本化迁移，用 SQL 脚本管理表结构变更历史 | ★ |
| Springdoc | 自动从代码注解生成 OpenAPI 文档 | ★ |
| 阿里云 OSS SDK | file-service 中负责文件上传到对象存储 | ★ |
| Docker Compose | 本地开发时一键启动所有中间件（MySQL / Redis / Nacos / RabbitMQ） | ★★ |

### 中间件（由 `docker-compose.yml` 统一管理）

| 中间件 | 端口 | 用途 |
|---|---|---|
| MySQL | 3306 | 存储所有业务数据 |
| Redis | 6379 | 缓存和 JWT 黑名单 |
| Nacos | 8848 / 9848 | 服务注册发现 / 配置中心 |
| RabbitMQ | 5672 / 15672 | 消息队列 / Web 管理台 |

---

## 第二部分：模块关系

### 前端：Feature-Sliced Design 分层结构

前端采用 Feature-Sliced Design（FSD）架构，核心规则是依赖只能从上往下，上层可以使用下层，下层不能反向引用上层。

```
┌─────────────────────────────────────────────────────┐
│                    app (引导层)                       │
│  main.ts / App.vue / router / providers / styles     │
└──────────────────────┬──────────────────────────────┘
                       │ 引导并组装
┌──────────────────────▼──────────────────────────────┐
│                   pages (页面层)                      │
│  home / article / auth / category / profile /        │
│  review / search / system                            │
└───┬──────────┬────────────┬─────────────────────────┘
    │ 组合      │ 组合        │ 组合
    ▼          ▼            ▼
┌────────┐ ┌──────────┐ ┌────────────┐
│widgets │ │ features │ │  entities  │
│16个组件 │ │ 10个功能 │ │ 4个领域模型│
└────┬───┘ └────┬─────┘ └─────┬──────┘
     │          │              │
     └──────────┴──────┬───────┘
                       │ 全部依赖
           ┌───────────▼────────────┐
           │    shared (基础设施)    │
           │ api / components /      │
           │ composables / utils /   │
           │ config / constants      │
           └────────────────────────┘
                       │
           ┌───────────▼────────────┐
           │   stores (跨页状态)    │
           │ auth / ui / editor /   │
           │ draft / session        │
           └────────────────────────┘
```

简单理解：`pages` 负责"把东西拼在一起"，`features` 负责"一个用户动作"（比如登录、提交审核），`entities` 负责"一种数据长什么样"（比如文章、用户），`shared` 放所有人都要用的基础设施，`stores` 放需要跨页面共享的状态。

### 后端：微服务拓扑

```
                    ┌─────────────────────┐
                    │   gateway-service    │
                    │ (公网入口 :8080)      │
                    └──────────┬──────────┘
                               │ 路由分发
        ┌──────────┬───────────┼────────────┬───────────┐
        ▼          ▼           ▼            ▼           ▼
┌────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│auth-service│ │content-  │ │review-   │ │search-   │ │file-     │
│ :8081      │ │service   │ │service   │ │service   │ │service   │
│用户/认证    │ │:8082     │ │:8083     │ │:8084     │ │:8085     │
└─────┬──────┘ │文章/草稿  │ │审核/日志  │ │公开搜索   │ │上传/静态  │
      │        └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────────┘
      │             │            │             │
      │             │  RabbitMQ  │             │  ┌──────────────┐
      │             │◄──事件────►│             │  │notification- │
      │             │            │             │  │service :8086 │
      │             │◄───────────┼─────────────┤  │通知投递       │
      │             │            │             │  └──────────────┘
      │             ▼            ▼             ▼
  ┌───┴────────────────────────────────────────────────┐
  │                   MySQL (content_platform)          │
  │ users / articles / review_logs / review_tasks /     │
  │ event_outbox / event_consume_log / notifications /  │
  │ notification_deliveries                             │
  └────────────────────────────────────────────────────┘
```

前端只需要和 `gateway-service`（端口 8080）通信，由网关负责转发到对应的业务服务。各业务服务之间通过 RabbitMQ 传递异步事件，不直接互相调用（需要同步查数据时才用 Feign）。

### 后端：公共模块结构

```
┌─────────────┐  ┌──────────────────┐  ┌──────────────┐
│platform-    │  │platform-web-     │  │platform-     │
│kernel       │  │support           │  │events        │
│常量/Result/ │  │JWT过滤器/Feign/  │  │Outbox/MQ拓扑 │
│异常/枚举    │  │Security配置      │  │事件发布/消费  │
└──────┬──────┘  └────────┬─────────┘  └──────┬───────┘
       │ 所有服务依赖      │ Web服务依赖         │ 需要MQ的服务依赖
       ▼                  ▼                    ▼

┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│auth-contract │  │content-      │  │review-       │
│              │  │contract      │  │contract      │
│Feign Client  │  │Feign Client  │  │Feign Client  │
│用户查询契约   │  │文章查询契约   │  │审核查询契约   │
└──────────────┘  └──────────────┘  └──────────────┘
```

`platform-kernel` 是最底层的公共库，定义了统一的 `Result` 响应格式、异常体系和枚举常量，所有服务都依赖它。`*-contract` 模块是服务对外暴露的 Feign 接口定义，其他服务通过引入 contract 依赖来调用该服务，而不是直接写死 HTTP URL。

---

## 第三部分：阅读顺序

读代码不要从头逐行读，要沿着有意义的线索走。下面给出三个阶段，每个阶段有明确的目标，读完一个阶段再进下一个。

### 阶段一：理解骨架

先花一两个小时把这几个文件过一遍，目的是建立全局心智模型，知道项目"长什么样子"。不需要读懂每一行，能理解结构就够了。

1. `E:\code\now-demo\pom.xml` — 后端所有模块一目了然，看 `<modules>` 段落
2. `E:\code\now-demo\docker-compose.yml` — 项目依赖哪些基础设施、用什么端口
3. `E:\code\now-demo\deploy\sql\init.sql` — 数据库有哪些表、字段是什么含义
4. `C:\...\now\package.json` — 前端有哪些依赖，`scripts` 里有哪些可用命令
5. `C:\...\now\vite.config.ts` — 开发服务器配置，特别是代理规则（`/api` 转发到哪里）
6. `C:\...\now\src\app\main.ts` — 前端启动入口，看插件挂载顺序
7. `C:\...\now\src\app\router\index.ts` — 所有路由定义，了解页面结构
8. `E:\...\gateway-service\GatewayRouteConfig.java` — 后端路由总览，URL 路径和服务的映射关系
9. `E:\...\platform-kernel\Result.java` — 统一响应模型，所有接口都用这个格式返回数据

### 阶段二：核心流程

骨架清楚之后，选一条完整的业务链路通读。推荐从"登录"开始，因为它覆盖了前端到后端的完整路径，而且逻辑相对独立、没有太多分支。

10. `C:\...\now\src\pages\auth\LoginPage.vue` — 登录页面，看它如何组合组件
11. `C:\...\now\src\features\auth\ui\LoginForm.vue` — 登录表单组件，表单校验和提交逻辑
12. `C:\...\now\src\features\auth\model\auth.mapper.ts` — 看 DTO 和视图模型之间的转换规则
13. `C:\...\now\src\shared\api\modules\auth.ts` — 前端发出的实际 HTTP 请求
14. `C:\...\now\src\shared\api\request.ts` → `http.ts` → `response.ts` — 请求是怎么发出去的、响应是怎么被处理的
15. `C:\...\now\src\stores\auth.ts` — 登录成功后 token 和用户信息存在哪里
16. `E:\...\gateway-service\GatewayAuthFilter.java` — 请求到达网关后的 JWT 校验逻辑
17. `E:\...\auth-service\AuthController.java` — 后端接收登录请求的入口
18. `E:\...\auth-service\AuthServiceImpl.java` — 登录的具体业务逻辑（查库、校验密码、签发 token）
19. `E:\...\auth-service\UserMapper.java` + `User.java` — 数据层，MyBatis Plus 怎么操作 users 表

### 阶段三：细节补全

前两个阶段读完后，你对项目主干已经有清晰的认识。第三阶段是按需选读，遇到哪块不清楚就去看对应的文件。

20. `C:\...\now\src\app\providers\setupApiSideEffects.ts` — 全局 401 / 403 错误怎么处理（未登录跳登录页、无权限跳 forbidden）
21. `C:\...\now\src\app\router\guards.ts` — 路由守卫，`requiresAuth` 和 `publicOnly` 两个 meta 字段的逻辑
22. `C:\...\now\src\shared\api\adapters.ts` — 后端响应在进入业务代码之前的数据标准化层
23. `C:\...\now\src\shared\api\queryKeys.ts` — Vue Query 缓存 key 的统一管理，做缓存失效时用到
24. `E:\...\platform-events\` — 了解 Outbox 模式和 RabbitMQ 的拓扑定义
25. `E:\...\*-contract\` — 各服务对外暴露的 Feign 接口，看服务边界在哪里
26. `E:\...\content-service\` — 文章的创建、编辑、发布完整流程
27. `E:\...\review-service\` — 审核队列、审核操作和审核日志流程
