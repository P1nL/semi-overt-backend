# API 与权限矩阵

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 路由与认证

公网请求进入 GatewayRouteConfig；ApiCompatibilityWebFilter 把旧 /api 前缀及 review/upload/search 等别名归一化，再进入鉴权和路由。下表使用 /api/v1 规范路径；不是移除旧别名的声明。

GatewayAuthFilter 清理外来内部身份头，通过 SessionAuthorityClient 请求 Auth 校验设备会话，随后注入可信身份。不是网关独立验 JWT/Redis 黑名单模型。登录、刷新、退出等认证入口由 AuthController 承担；GatewayAuthController 不是当前退出入口。

## 外部业务接口

| 服务 | 方法与路径 | 访问约束 |
| --- | --- | --- |
| Auth | POST /auth/register-code、/auth/register、/auth/login、/auth/forgot-password、/auth/reset-password | 无需已有登录；仍有校验与预算 |
| Auth | POST /auth/refresh、/auth/logout | refresh Cookie 与来源校验，不要求旧 access token 仍有效 |
| Auth | GET /users/me；PUT /users/me/profile（兼容 /users/me） | 登录 |
| Auth | GET /users/{identifier}/profile | 公开；私有字段不得泄露 |
| Content | GET /home、/categories/**、/articles/{数字ID} | 公开路由；资源可见性由服务校验 |
| Content | POST /articles；PUT /articles/{id}/draft；GET /articles/drafts | 登录，草稿有作者/版本约束 |
| Content | POST /articles/{id}/submit、/articles/{id}/cancel-review；DELETE /articles/{id} | 作者及状态校验 |
| Content | DELETE /admin/articles/{id} | ADMIN |
| Content | POST /articles/ai-polish | 登录；独立模型配置、限流与正文限制 |
| Review | GET /reviews/pending | ADMIN |
| Review | POST /reviews/{id}/decision（兼容 /action）；GET /reviews/{id}/decision-status | ADMIN，分配与决定基线约束 |
| Review | GET /reviews/{数字ID}/logs | 网关允许匿名；下游控制记录可见性，不等于所有日志公开 |
| Search | GET /search/articles、/search/users | 公开，有请求预算 |
| File | POST /uploads/images | 登录，上传预算与图片校验 |
| File | GET /static/uploads/** | 静态路径无 /api/v1 前缀 |
| Notification | GET /notifications | 登录，仅当前用户 |

除静态路径外，表中业务路径统一加 /api/v1 前缀。请求体字段以各 Controller 的 req DTO 为准，审核须特别核对 decisionId、submissionId、expectedVersion。

## 错误与边界

- 401：受保护接口缺少有效会话。不要把所有匿名公开请求都当作 401。
- 403：身份存在但权限不足；业务服务还会校验作者、审核分配等。
- 409：版本或决定冲突；不能盲目覆盖。
- 429：预算耗尽，关注 Retry-After。
- 503：认证/预算依赖不可用，或业务暂时无法确认；不是伪装成功或自动退出的依据。
- /internal/** 不对公网开放；内部校验同时需要服务端令牌与可信来源。
- 非法 Bearer 的实际返回还取决于 Auth 校验失败路径；不要沿用旧手册“所有公开接口携带无效 token 必然 401”的绝对断言。

源码：[路由](../../gateway-service/src/main/java/com/platform/gateway/config/GatewayRouteConfig.java)、[兼容映射](../../gateway-service/src/main/java/com/platform/gateway/filter/ApiCompatibilityWebFilter.java)、[鉴权](../../gateway-service/src/main/java/com/platform/gateway/filter/GatewayAuthFilter.java)。
