# 02 模块地图

## 为什么读这份文档

这份文档把“服务职责、端口、依赖、路由、内部接口”一次讲清。重点不是代码细节，而是让你先知道某个能力应该归谁、某个请求会流向哪里。

## 模块与端口

当前固定端口与 [dev-up.ps1](../../scripts/dev-up.ps1) 保持一致：

- `gateway-service`：`8080`
- `auth-service`：`8081`
- `content-service`：`8082`
- `review-service`：`8083`
- `search-service`：`8084`
- `file-service`：`8085`
- `notification-service`：`8086`

## 网关路由边界

当前网关路由定义在 [GatewayRouteConfig.java](../../gateway-service/src/main/java/com/platform/gateway/config/GatewayRouteConfig.java)。

固定映射：

- `/api/v1/auth/**`、`/api/v1/users/**` -> `auth-service`
- `/api/v1/home`、`/api/v1/categories/**`、`/api/v1/articles/**` -> `content-service`
- `/api/v1/reviews/**` -> `review-service`
- `/api/v1/search/**` -> `search-service`
- `/api/v1/uploads/**`、`/static/uploads/**` -> `file-service`

## 公开能力与内部能力

### 公开能力

公开能力是前端或浏览器直接访问的接口。

典型公开入口：

- 注册、登录、找回密码
- 首页、分类、文章详情、用户主页
- 搜索
- 上传与静态文件访问

### 内部能力

内部能力是服务之间协作接口，不是前端契约。

当前典型内部接口：

- `POST /internal/users/batch`
- `GET /internal/articles/{id}/review-snapshot`
- `POST /internal/articles/{id}/apply-review-result`
- `POST /internal/articles/profile-page`
- `GET /internal/reviews/articles/{id}/latest`
- `POST /internal/reviews/tasks/upsert`
- `POST /internal/reviews/tasks/remove`

## 内部头协议

内部头常量统一定义在 [HeaderNames.java](../../common/src/main/java/com/platform/common/constant/HeaderNames.java)：

- `X-User-Id`
- `X-Username`
- `X-User-Role`
- `X-Trace-Id`

这些头的意义是：

- 在网关与下游之间传递可信用户身份
- 在整条链路中传递追踪 ID

## 各服务依赖什么

### `gateway-service`

依赖：

- Nacos
- Redis

职责：

- 路由
- JWT 校验
- 限流

### `auth-service`

依赖：

- MySQL
- Redis
- Nacos
- Mail

职责：

- 用户与认证真源

### `content-service`

依赖：

- MySQL
- Redis
- RabbitMQ
- Nacos

职责：

- 文章主状态与内容真源

### `review-service`

依赖：

- MySQL
- RabbitMQ
- Nacos
- 内部调用 `content-service`、`auth-service`

职责：

- 审核动作、审核日志、审核任务投影

### `search-service`

依赖：

- Elasticsearch
- MySQL
- RabbitMQ
- Nacos
- 内部调用 `auth-service`

职责：

- 公开搜索与索引同步

### `file-service`

依赖：

- 本地文件系统
- Nacos

职责：

- 上传入口与静态文件访问映射

### `notification-service`

依赖：

- MySQL
- RabbitMQ
- Nacos

职责：

- 通知派生数据落库

## 协作原则

- 文章状态真源只在 `content-service`
- 用户真源只在 `auth-service`
- 搜索与通知属于派生数据
- 网关统一处理公网入口问题

## 读完后你应该知道什么

- 某个 URL 最终会被转发到哪个服务
- 哪些能力是公开入口，哪些是内部协作
- 某类需求应先从哪个服务入手
