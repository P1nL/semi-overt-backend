# 01 项目骨架图

## 为什么读这篇

这篇用来建立“当前仓库到底是怎么组织的”这个最基础的坐标系。你在读任何逐文件说明之前，先把模块边界、启动入口和运行层次对齐，后面就不容易把代码看串。

## 当前真实结构

当前主实现是根 [pom.xml](../../pom.xml) 下的 Maven 多模块工程，核心模块如下：

- `gateway-service`：公网统一入口
- `auth-service`：认证与用户域
- `content-service`：内容域
- `review-service`：审核域
- `search-service`：搜索投影与公开搜索
- `file-service`：上传与静态访问映射
- `notification-service`：通知派生数据
- `common`：共享契约与公共支持代码

旧单体根目录 `src/` 已经移除。当前联调、测试、发布、上线都只围绕多模块微服务工程展开。

## 启动入口与运行层次

运行层次可以按四层理解：

1. 中间件层：MySQL、Redis、RabbitMQ、Elasticsearch、Nacos
2. 业务服务层：`auth/content/review/search/file/notification`
3. 统一入口层：`gateway-service`
4. 可选外部反向代理层：Nginx

本地默认启动方式是：

- 中间件由 [docker-compose.yml](../../docker-compose.yml) 管理
- 业务服务由 [scripts/dev-up.ps1](../../scripts/dev-up.ps1) 启动

Linux 发布基线是：

- 每个服务构建成 Jar
- 用 [scripts/run-service.sh](../../scripts/run-service.sh) 执行 `java -jar`
- 可选由 [deploy/nginx/now-demo.conf](../../deploy/nginx/now-demo.conf) 暴露外层入口

## 服务清单与端口

- `gateway-service`：`8080`
- `auth-service`：`8081`
- `content-service`：`8082`
- `review-service`：`8083`
- `search-service`：`8084`
- `file-service`：`8085`
- `notification-service`：`8086`

这些默认端口来自各模块自己的 `application.yml`，也都可以被 `SERVER_PORT` 覆盖。

## 模块角色怎么分

### `gateway-service`

- 负责公网流量进入系统后的第一层处理
- 做 JWT 解析、路由转发、限流、统一异常输出、内部头注入
- 它不拥有业务真源数据，主要承担入口治理

### `auth-service`

- 用户、认证、密码找回、用户资料属于这里
- 其他服务如果只需要用户摘要，不应该直连用户表，而是走内部接口

### `content-service`

- 文章状态真源在这里
- 首页、分类、详情、草稿、提审都从这里出发
- 审核和搜索都围绕内容域状态变化来派生

### `review-service`

- 负责待审任务、审核动作、审核日志
- 自己不保存文章正文真源，而是消费内容域提审事件并维护自己的任务视图

### `search-service`

- 负责公开搜索接口
- 维护“已发布文章”的 Elasticsearch 投影
- 不直接决定文章是否发布，而是跟随内容状态事件同步索引

### `file-service`

- 负责上传校验、落盘和静态访问映射
- 当前是本地磁盘方案，不是对象存储方案

### `notification-service`

- 负责消费文章状态变化事件并生成通知
- 通知是派生数据，不是内容真源

### `common`

- 放共享常量、事件模型、内部 DTO、公共过滤器和支持服务
- 它是跨服务契约层，不应该承载具体业务规则真源

## 你应该怎样用这张骨架图

- 如果你要改公网请求行为，先看 `gateway-service`
- 如果你要改用户和 token 规则，先看 `auth-service`
- 如果你要改文章状态、提审、首页和详情，先看 `content-service`
- 如果你要改审核动作与审核链路，先看 `review-service`
- 如果你要改公开搜索结果，先看 `search-service`
- 如果你要改文件上传和访问，先看 `file-service`
- 如果你要改通知生成和投递记录，先看 `notification-service`
- 如果你一改就跨服务联动，先看 `common`
