# 基础设施线程落地说明

## 已落地模块

- `common`：统一返回、异常、公共枚举、上下文、Header 协议、Feign 透传、下游 Header 认证过滤器
- `gateway-service`：路由、JWT 校验、Redis 黑名单、TraceId、请求头重写、本地 `logout`
- `auth-service`：登录注册、用户信息、内部用户摘要查询
- `content-service`：文章、草稿、首页、分类、内部审核快照/审核结果应用
- `review-service`：待审核、审核动作、审核日志、内部最新审核原因
- `search-service`：占位搜索服务
- `file-service`：上传服务
- `notification-service`：预留启动骨架

## 当前内部接口

- `POST /internal/users/batch`
- `GET /internal/articles/{id}/review-snapshot`
- `POST /internal/articles/{id}/apply-review-result`
- `GET /internal/reviews/articles/{id}/latest`

## 网关路由

- `/api/v1/auth/register|login|forgot-password|reset-password` -> `auth-service`
- `/api/v1/auth/logout` -> `gateway-service` 本地处理
- `/api/v1/users/**` -> `auth-service`
- `/api/v1/home`、`/api/v1/categories/**`、`/api/v1/articles/**` -> `content-service`
- `/api/v1/reviews/**` -> `review-service`
- `/api/v1/search/**` -> `search-service`
- `/api/v1/uploads/**`、`/static/uploads/**` -> `file-service`

## Nacos dataId 约定

共享配置：

- `shared-common.yaml`
- `shared-db.yaml`
- `shared-redis.yaml`
- `shared-jwt.yaml`

服务配置：

- `gateway-service.yaml`
- `auth-service.yaml`
- `content-service.yaml`
- `review-service.yaml`
- `search-service.yaml`
- `file-service.yaml`
- `notification-service.yaml`

环境覆盖：

- `${serviceName}-${profile}.yaml`

## 当前边界说明

- 下游服务已经移除 JWT 解析入口，统一信任网关注入的 `X-User-*` 与 `X-Trace-Id`
- `content-service` 已通过 Feign 获取作者摘要和最新审核原因
- `review-service` 已通过 Feign 获取审核快照并调用内容服务应用审核结果
- `auth-service` 仍保留现有用户主页聚合逻辑，后续可继续下沉为内部聚合接口
- `review-service` 的待审核分页仍直接读 `articles`，后续可替换为 `review_tasks` 投影
