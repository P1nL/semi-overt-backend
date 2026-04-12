# 架构设计分析

> 面向初级开发者。本文梳理前后端各自的分层结构、每层的职责边界、公共基础设施的定位，最后用一个完整的登录场景把所有环节串起来。

---

## 第一部分：架构图

### 前端架构图 — Feature-Sliced Design (FSD)

FSD 是一套强制依赖方向的目录分层规范。规则只有一条：**上层可以依赖下层，下层不能反向依赖上层**。这样做的好处是任何一层的改动都不会意外影响到它下面的层。

```
层级依赖方向: 上层 -> 下层（只能向下依赖，不能反向）

+---------------------------------------------+
|  app 层 (引导)                               |
|  - main.ts: 创建Vue实例，注册插件            |
|  - App.vue: 根组件，管理Sheet路由            |
|  - router/: 路由定义和守卫                   |
|  - providers/: Pinia/VueQuery/API副作用注册  |
|  - styles/: 全局主题CSS变量、基础样式         |
+----------------------+----------------------+
                       |
                       v
+---------------------------------------------+
|  pages 层 (路由级组件)                       |
|  - 8个页面模块: home/article/auth/category/  |
|    profile/review/search/system              |
|  - 只做编排组合，不含业务逻辑                |
+----------------------+----------------------+
                       |
                       v
+---------------------------------------------+
|  widgets 层 (大型可复用UI区域)               |
|  - 16个widget: app-header/hero-section/     |
|    article-reader/page-sheet/toast-stack/   |
|    profile-header/review-queue-strip 等     |
+----------------------+----------------------+
                       |
                       v
+---------------------------------------------+
|  features 层 (单一用户操作)                  |
|  - 10个feature: auth/article-editor/        |
|    article-submit/review-action/            |
|    theme-switch/draft-box/profile-edit/     |
|    toc-sync/article-cancel-review/          |
|    admin-article-delete                     |
|  - 每个feature有: model/ + ui/ + api/       |
+----------------------+----------------------+
                       |
                       v
+---------------------------------------------+
|  entities 层 (领域模型)                      |
|  - 4个entity: article/category/review/user  |
|  - 每个entity有: model/(mapper+types) + ui/ |
|  - queries.ts: 公共Vue Query hooks          |
+----------------------+----------------------+
                       |
                       v
+---------------------------------------------+
|  shared 层 (基础设施)                        |
|  - api/: HTTP客户端/请求/响应/适配器/模块   |
|  - components/: 基础UI组件                  |
|  - composables/: 组合式函数                 |
|  - utils/: 工具函数                         |
|  - config/: 环境配置                        |
|  - constants/: 全局常量                     |
|  - types/: 类型定义                         |
|  - image-upload/: 图片上传                  |
|  - lib/: 第三方库封装                       |
+---------------------------------------------+

跨切面: stores/ (Pinia 状态管理)
  - auth.ts / ui.ts / editor.ts / draft.ts / session.ts
  stores 不属于任何单一层，各层均可按需引入
```

### 后端架构图 — 微服务分层

每个业务服务（auth-service、content-service、review-service 等）内部都遵循相同的四层结构。gateway-service 是所有请求的统一入口。

```
+----------------------------------------------+
|              网关层 (gateway-service)          |
|  - 路由分发（按path前缀转发到对应服务）        |
|  - JWT校验 + Redis黑名单                      |
|  - 内部头注入 (X-User-Id/Username/Role)       |
|  - 限流 + TraceId透传                         |
+---------------------+------------------------+
                      |
                      v
+----------------------------------------------+
|           Controller 层 (每个服务)             |
|  - 接收HTTP请求，参数校验(@Valid)              |
|  - 调用Service，包装Result返回                |
+---------------------+------------------------+
                      |
                      v
+----------------------------------------------+
|            Service 层 (每个服务)               |
|  - 核心业务逻辑                               |
|  - 事务管理(@Transactional)                   |
|  - 调用Mapper做数据操作                       |
|  - 通过Outbox发布事件                         |
|  - 通过Feign调用其他服务                      |
+---------------------+------------------------+
                      |
                      v
+----------------------------------------------+
|     Mapper/Repository 层 (每个服务)            |
|  - MyBatis Plus BaseMapper                    |
|  - 单表CRUD自动生成                           |
|  - 复杂查询用LambdaQueryWrapper               |
+---------------------+------------------------+
                      |
                      v
+----------------------------------------------+
|         数据库 (MySQL content_platform)        |
|  - users / articles / review_logs /           |
|    review_tasks / event_outbox /              |
|    event_consume_log / notifications /        |
|    notification_deliveries                    |
+----------------------------------------------+
```

---

## 第二部分：职责说明表格

### 前端分层职责

| 层名 | 应该做什么 | 不应该做什么 |
|------|-----------|-------------|
| app | 创建Vue实例、注册插件、定义路由、注册全局样式、设置API副作用 | 不应包含任何业务逻辑或UI组件 |
| pages | 组合widgets/features/entities构成完整页面，处理加载/错误/空状态 | 不应包含业务逻辑（逻辑在feature的model/里），不应直接调API |
| widgets | 提供跨页面复用的大型UI区域（如header、reader、sheet） | 不应包含仅单页面使用的UI，不应直接调API（通过props/events通信） |
| features | 封装单个用户操作的完整逻辑(model/) + UI(ui/) + API(api/) | 不应跨feature互相引用，不应直接操作其他feature的状态 |
| entities | 定义领域模型类型、DTO到VM转换Mapper、最小展示组件 | 不应包含用户交互逻辑（那是feature的事），不应直接调API |
| shared | 提供API客户端、基础组件、工具函数、常量、类型 | 不应包含业务逻辑，不应依赖任何上层（pages/widgets/features/entities） |
| stores | 管理跨页面需要共享的状态（auth/ui/editor等） | 不应包含UI渲染逻辑 |

### 后端分层职责

| 层名 | 应该做什么 | 不应该做什么 |
|------|-----------|-------------|
| gateway-service | 路由、鉴权、限流、TraceId透传、内部头注入、logout | 不应包含业务数据CRUD，不应访问数据库 |
| Controller | 接收请求、参数校验、调用Service、包装Result返回 | 不应包含业务逻辑，不应直接操作数据库 |
| Service | 核心业务逻辑、事务管理、事件发布、跨服务调用 | 不应直接构造HTTP响应，不应包含SQL |
| Mapper | 数据库操作、SQL查询 | 不应包含业务逻辑 |
| *-contract | 定义跨服务调用的Feign Client和DTO | 不应包含业务逻辑实现 |
| platform-kernel | 共享常量、Result模型、异常、枚举 | 不应包含Spring Bean编排 |
| platform-web-support | Web/Security/Feign公共配置 | 不应包含业务逻辑 |
| platform-events | Outbox、MQ拓扑、事件发布消费基础设施 | 不应包含具体业务事件处理逻辑 |

---

## 第三部分：公共/基础设施代码清单

公共模块是整个项目的"地基"，任何业务代码都不应该绕过它们自己实现相同的功能。

### 前端公共模块

**1. 统一HTTP客户端 — `shared/api/http.ts`**

Axios实例，`baseURL=/api/v1`，超时15s。

- 请求拦截器：自动附加 `Authorization: Bearer <token>`
- 响应拦截器：尝试持久化刷新token，触发401/403/404副作用

**2. 请求封装 — `shared/api/request.ts`**

对外暴露 `get` / `post` / `put` / `patch` / `delete` / `upload` 六个方法，自动解包 `{code, message, data}` 响应信封，业务代码拿到的直接是 `data` 字段。

**3. 响应处理 — `shared/api/response.ts`**

- `unwrapApiResponse`：检查 `code === 200`，否则抛 `ApiBusinessError`
- `runApiSideEffects`：401清auth跳登录，403跳forbidden，404跳not-found
- 副作用通过 `registerApiSideEffectHandlers` 延迟注册，注册点在 `app/providers/setupApiSideEffects.ts`，避免循环依赖

**4. 后端响应适配层 — `shared/api/adapters.ts`**

在 entity mapper 运行之前，先把后端字段风格转为前端 ViewModel，处理 null/undefined 安全、资源URL拼接、数值类型转换。

**5. API模块 — `shared/api/modules/*.ts`**

每个文件对应一个后端服务的接口：`auth.ts` / `article.ts` / `user.ts` / `home.ts` / `category.ts` / `search.ts` / `review.ts` / `upload.ts`。

**6. 缓存Key管理 — `shared/api/queryKeys.ts`**

统一管理 Vue Query 缓存 key，所有 `invalidateQueries` 调用都从这里取 key，避免字符串拼写错误导致缓存失效不生效。

**7. 全局副作用注册 — `app/providers/setupApiSideEffects.ts`**

- 401：清除 auth store，跳转登录页（附 `redirect` 参数）
- 403：标记 forbidden 消息，跳转 forbidden 页
- 404：跳转 not-found 页

**8. 路由守卫 — `app/router/guards.ts`**

- Drawer 关闭守卫：导航前先等 Drawer 动画完成
- Auth 守卫：`requiresAuth` / `publicOnly` / `roles` 三种 meta 字段的检查
- 文档标题自动设置

### 后端公共模块

**1. 统一响应模型 — `platform-kernel: Result.java`**

`{code, message, data}` 三字段信封。提供静态工厂方法：`ok()` / `fail()` / `badRequest()` / `unauthorized()` / `forbidden()` / `notFound()` / `conflict()` / `tooManyRequests()` / `serverError()`。所有 Controller 返回值统一用这个类型。

**2. 全局异常处理 — `platform-kernel: BusinessException.java`**

业务异常基类，携带 code 和 message。静态工厂：`badRequest` / `conflict` / `notFound` / `forbidden` / `tooManyRequests` / `serverError`。Service 层直接 `throw BusinessException.notFound(...)` 即可，全局 ExceptionHandler 负责捕获并转换为 Result 返回。

**3. 内部头协议 — `platform-kernel: HeaderNames.java`**

定义网关注入、服务间传递的标准头名称：`X-User-Id` / `X-Username` / `X-User-Role` / `X-Trace-Id`。所有服务读取身份信息时必须从这几个头取，不允许各自另起字段。

**4. 网关认证过滤器 — `gateway-service: GatewayAuthFilter.java`**

全局过滤器，执行顺序：清理伪造头 -> 解析JWT -> 检查Redis黑名单 -> 注入身份头 -> token快过期时刷新。白名单路径（如 `/auth/login`）直接放行。

**5. 事件基础设施 — `platform-events`**

- Outbox模式：业务操作和事件写入同一个本地事务，保证消息不丢失
- RabbitMQ拓扑：主队列 + 重试队列 + 死信队列
- 通用消费执行器：幂等消费（通过 `event_consume_log` 去重）、消费日志

**6. 请求头认证过滤器 — `platform-web-support`**

从 `X-User-Id` / `X-Username` / `X-User-Role` 头提取用户身份，注入 Spring Security Context，让 Service 层可以直接用 `@AuthenticationPrincipal` 拿到当前用户。Feign 拦截器负责在调用其他服务时把这些头自动转发出去。

**7. 服务间契约 — `auth-contract / content-contract / review-contract`**

每个服务对外提供一个 contract 模块，里面只有 Feign Client 定义和请求/响应 DTO。调用方只依赖 contract 模块，不依赖实现模块。这样被调用方改内部实现时，只要 contract 不变，调用方无需重新编译。

---

## 第四部分：数据流场景串联

用「用户登录」把整个请求链路串一遍，从浏览器点击到最终跳转，经过的每一个模块都标出来。

```
用户在浏览器点击「登录」按钮
        |
        v
[前端 LoginForm.vue]
  1. 表单验证 -> mapLoginFormToDto() 转换
  2. 调用 authApi.login(dto)
        |
        v
[前端 shared/api/modules/auth.ts]
  3. request.post('/auth/login', dto, {withAuth: false})
        |
        v
[前端 shared/api/request.ts]
  4. 调用 http.post() -> 自动跳过token注入(withAuth:false)
        |
        v
[前端 shared/api/http.ts - Axios]
  5. POST http://localhost:5173/api/v1/auth/login
        |
        v (Vite代理到 localhost:8080)
        |
[后端 gateway-service :8080]
  6. GatewayAuthFilter: /api/v1/auth/login 命中白名单，放行
  7. 路由规则转发到 auth-service :8081
        |
        v
[后端 auth-service: AuthController]
  8. @PostMapping("/login") -> @Valid校验 -> authService.login(req)
        |
        v
[后端 auth-service: AuthServiceImpl]
  9. 判断account是否含@：是->按email查，否->按username查
  10. UserMapper.selectOne() -> SELECT * FROM users WHERE username/email = ?
  11. passwordEncoder.matches() 验证BCrypt密码
  12. jwtHelper.createToken() 签发JWT
  13. 构建 AuthResp{token, userId, username, nickname, email, role, avatarUrl}
        |
        v
[后端 auth-service: AuthController]
  14. Result.ok(authResp) -> {code:200, message:"success", data:{...}}
        |
        v (响应原路返回，经过gateway透传)
        |
[前端 shared/api/http.ts - 响应拦截器]
  15. tryPersistRefreshedToken() 检查响应头是否有刷新token
        |
        v
[前端 shared/api/response.ts]
  16. unwrapApiResponse(): 检查code===200 -> 返回data字段
        |
        v
[前端 shared/api/adapters.ts]
  17. normalizeAuthResp(): 后端字段 -> 前端AuthRespDto
        |
        v
[前端 features/auth/model/auth.mapper.ts]
  18. mapAuthRespToSession(): AuthRespDto -> AuthSessionVm
        |
        v
[前端 stores/auth.ts]
  19. authStore.setAuth(): 存token到localStorage，存user到store
        |
        v
[前端 LoginForm.vue]
  20. router.push(redirect || '/home')：跳转到目标页
  21. toast.success('登录成功')
```

### 这个场景揭示了几个关键设计决策

**前端分层体现在步骤 1 到 5：** 表单组件（LoginForm.vue）只负责收集数据和调用API模块，它不知道HTTP细节。API模块（auth.ts）只知道"调什么接口"，不知道底层用的是Axios还是fetch。request.ts 处理信封解包，http.ts 处理token注入，每一层只做自己该做的事。

**适配器在步骤 17 的位置：** 适配器（adapters.ts）在 mapper（auth.mapper.ts）之前运行。原因是适配器负责"让后端字段变得安全可用"，mapper负责"把DTO转成ViewModel"，两件事分开才能各自单独测试。

**网关鉴权透明体现在步骤 6：** 登录接口命中白名单直接放行，业务服务（auth-service）完全不需要关心"这个请求有没有带token"。对于需要鉴权的接口，gateway 会在请求到达业务服务之前就注入好 `X-User-Id` 等头，业务服务直接读头就能拿到身份，不需要再解析JWT。
