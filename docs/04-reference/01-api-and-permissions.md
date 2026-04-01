# API 与权限矩阵

适合谁看：联调、排鉴权问题、梳理网关行为或准备改接口的人。  
读完能解决什么问题：知道外部 API 走向、哪些接口能匿名访问、哪些必须登录或管理员权限，以及无效 token 的处理规则。

## 外部入口总览

当前公网路由由 [GatewayRouteConfig.java](../../gateway-service/src/main/java/com/platform/gateway/config/GatewayRouteConfig.java) 注册：

- `/api/v1/auth/**` -> `auth-service`
- `/api/v1/users/**` -> `auth-service`
- `/api/v1/home` -> `content-service`
- `/api/v1/categories/**` -> `content-service`
- `/api/v1/articles/**` -> `content-service`
- `/api/v1/reviews/**` -> `review-service`
- `/api/v1/search/**` -> `search-service`
- `/api/v1/uploads/**` -> `file-service`
- `/static/uploads/**` -> `file-service`

额外说明：

- `/api/v1/auth/logout` 由 `gateway-service` 自身提供
- `/internal/**` 在网关层直接返回 `404`，不会对外暴露

## 权限分层

### 公开接口

匿名可访问，但如果请求带了非法 token，网关仍返回 `401`。

当前公开接口包括：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `GET /api/v1/home`
- `GET /api/v1/categories/**`
- `GET /api/v1/articles/{id}`
- `GET /api/v1/users/{username}/profile`
- `GET /api/v1/search/**`
- `GET /api/v1/reviews/{articleId}/logs`
- `GET /static/uploads/**`

### 登录接口

需要合法用户身份：

- `GET /api/v1/users/me`
- `PUT /api/v1/users/me/profile`
- `POST /api/v1/articles`
- `PUT /api/v1/articles/{articleId}/draft`
- `GET /api/v1/articles/drafts`
- `POST /api/v1/articles/{articleId}/submit`
- `POST /api/v1/articles/{articleId}/cancel-review`
- `DELETE /api/v1/articles/{articleId}`
- `POST /api/v1/uploads/images`
- `POST /api/v1/auth/logout`

### 管理员接口

当前管理员路径是 `review-service` 的审核能力：

- `GET /api/v1/reviews/pending`
- `POST /api/v1/reviews/{articleId}/action`

注意：

- `GET /api/v1/reviews/{articleId}/logs` 只要求已登录，不要求管理员
- 网关层已对 `/api/v1/reviews/**` 做管理员限制，但日志查询被显式放行

## Controller 级外部接口清单

### `auth-service`

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `GET /api/v1/users/me`
- `PUT /api/v1/users/me/profile`
- `GET /api/v1/users/{username}/profile`

### `content-service`

- `GET /api/v1/home`
- `GET /api/v1/categories/{category}/articles`
- `POST /api/v1/articles`
- `PUT /api/v1/articles/{articleId}/draft`
- `GET /api/v1/articles/drafts`
- `GET /api/v1/articles/{articleId}`
- `POST /api/v1/articles/{articleId}/submit`
- `POST /api/v1/articles/{articleId}/cancel-review`
- `DELETE /api/v1/articles/{articleId}`

### `review-service`

- `GET /api/v1/reviews/pending`
- `POST /api/v1/reviews/{articleId}/action`
- `GET /api/v1/reviews/{articleId}/logs`

### `search-service`

- `GET /api/v1/search/articles`

### `file-service`

- `POST /api/v1/uploads/images`
- `GET /static/uploads/**`

### `gateway-service`

- `POST /api/v1/auth/logout`

## 网关鉴权语义

[GatewayAuthFilter.java](../../gateway-service/src/main/java/com/platform/gateway/filter/GatewayAuthFilter.java) 会：

- 生成或透传 `X-Trace-Id`
- 清理客户端自带的内部身份头
- 解析 `Authorization: Bearer <token>`
- 检查 Redis 黑名单 `jwt:blacklist:<token>`
- 给下游注入 `X-User-Id`、`X-Username`、`X-User-Role`
- 在 token 接近过期时回写 `New-Token`

## `401` 与 `403`

网关侧语义：

- 缺 token 访问受保护接口：`401`
- token 非法或黑名单命中：`401`
- 已登录但角色不足：`403`

下游服务也有自己的 Spring Security 配置，但外部联调时应优先看网关返回。

## 内部头协议

定义在 [HeaderNames.java](../../platform-kernel/src/main/java/com/platform/kernel/constant/HeaderNames.java)：

- `X-User-Id`
- `X-Username`
- `X-User-Role`
- `X-Trace-Id`

客户端不应自行伪造这些头，因为网关会先清掉同名头再重新注入。
