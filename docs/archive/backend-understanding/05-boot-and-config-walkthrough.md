# 05 启动与配置逐文件讲解

## 为什么读这篇

这篇用来回答“服务是怎么启动起来的、配置从哪里来、为什么本地能跑而上线还要再做一层准备”。如果你不了解启动入口和配置优先级，后面的业务文件即使看懂了，也很难判断问题到底是代码、配置还是环境。

## 本篇覆盖哪些文件

- [pom.xml](../../pom.xml)
- 各服务启动类
- 各模块 `application.yml`

## 根 `pom.xml`

文件位置：

- [../../pom.xml](../../pom.xml)

文件职责：

- 这是整个仓库的父工程
- 统一定义模块列表、依赖管理、插件配置和版本基线
- 当前微服务结构是否成立，先看这里，而不是去追溯已经移除的旧单体目录

关键行为：

- 把 `gateway-service`、`auth-service`、`content-service`、`review-service`、`search-service`、`file-service`、`notification-service`、`common` 收进同一个构建图
- 统一 Spring Boot、Spring Cloud、`springdoc` 等依赖版本

依赖关系：

- 所有服务模块都依赖它作为父 `pom`
- 它决定模块是否会参与当前真实构建

修改风险：

- 这里改错最容易引发全仓编译失败或运行期依赖冲突
- 版本升级看似只改一处，实际可能影响所有服务

常见改动入口：

- 新增模块
- 收敛依赖版本
- 修复跨模块依赖冲突

## 网关启动类

文件位置：

- [../../gateway-service/src/main/java/com/platform/gateway/GatewayServiceApplication.java](../../gateway-service/src/main/java/com/platform/gateway/GatewayServiceApplication.java)

文件职责：

- 声明 `gateway-service` 的 Spring Boot 启动入口

关键行为：

- 启动网关应用上下文
- 结合网关配置、路由配置和过滤器完成统一入口服务装配

依赖关系：

- 依赖网关模块自己的配置类
- 被 [scripts/dev-up.ps1](../../scripts/dev-up.ps1) 和 [scripts/run-service.sh](../../scripts/run-service.sh) 间接启动

修改风险：

- 启动类本身通常不需要频繁改动
- 一旦改包扫描范围或启用注解，可能造成配置类不生效

常见改动入口：

- 新增全局组件且需要调整扫描边界时

## 认证服务启动类

文件位置：

- [../../auth-service/src/main/java/com/platform/auth/AuthServiceApplication.java](../../auth-service/src/main/java/com/platform/auth/AuthServiceApplication.java)

文件职责：

- 声明 `auth-service` 的启动入口

关键行为：

- 装配认证、用户、Redis、邮件、JWT 等相关能力

依赖关系：

- 依赖 `auth-service` 自己的配置和 `common`

修改风险：

- 改扫描路径会直接影响 Controller、Service、Mapper 是否被发现

常见改动入口：

- 新接入认证相关基础设施时

## 内容服务启动类

文件位置：

- [../../content-service/src/main/java/com/platform/content/ContentServiceApplication.java](../../content-service/src/main/java/com/platform/content/ContentServiceApplication.java)

文件职责：

- 声明 `content-service` 的启动入口

关键行为：

- 装配内容域接口、草稿缓存、MQ 监听和定时任务

依赖关系：

- 依赖内容域自己的 Controller、Service、Mapper、任务与 MQ 组件

修改风险：

- 一旦定时任务或事件相关配置未被扫描，草稿刷回和状态消费都会出问题

常见改动入口：

- 新增内容域组件且包结构变化时

## 审核服务启动类

文件位置：

- [../../review-service/src/main/java/com/platform/review/ReviewServiceApplication.java](../../review-service/src/main/java/com/platform/review/ReviewServiceApplication.java)

文件职责：

- 声明 `review-service` 的启动入口

关键行为：

- 装配审核任务、审核日志、MQ 消费与发布组件

依赖关系：

- 依赖审核域的 Controller、Service、Mapper 与 MQ 组件

修改风险：

- 包扫描错误会让审核监听或审核接口缺失

常见改动入口：

- 新增审核投影或监听器时

## 搜索服务启动类

文件位置：

- [../../search-service/src/main/java/com/platform/search/SearchServiceApplication.java](../../search-service/src/main/java/com/platform/search/SearchServiceApplication.java)

文件职责：

- 声明 `search-service` 的启动入口

关键行为：

- 装配搜索查询、ES Repository、状态事件消费和启动回填逻辑

依赖关系：

- 依赖搜索模块自己的 Controller、Service、Mapper、Repository

修改风险：

- 启动回填如果未生效，会导致历史已发布文章与索引不一致

常见改动入口：

- 搜索投影初始化策略变化时

## 文件服务启动类

文件位置：

- [../../file-service/src/main/java/com/platform/file/FileServiceApplication.java](../../file-service/src/main/java/com/platform/file/FileServiceApplication.java)

文件职责：

- 声明 `file-service` 的启动入口

关键行为：

- 装配上传、存储配置与静态访问映射

依赖关系：

- 依赖上传 Controller、上传 Service、存储配置类

修改风险：

- 扫描错误会让上传接口和静态资源映射一起失效

常见改动入口：

- 切换存储方案时

## 通知服务启动类

文件位置：

- [../../notification-service/src/main/java/com/platform/notification/NotificationServiceApplication.java](../../notification-service/src/main/java/com/platform/notification/NotificationServiceApplication.java)

文件职责：

- 声明 `notification-service` 的启动入口

关键行为：

- 装配通知事件消费、通知落库与投递记录能力

依赖关系：

- 依赖通知事件监听器、事件服务和通知 Mapper

修改风险：

- 扫描错误会让通知消费和落库链路断掉

常见改动入口：

- 新增通知类型或投递策略时

## `gateway-service` 的 `application.yml`

文件位置：

- [../../gateway-service/src/main/resources/application.yml](../../gateway-service/src/main/resources/application.yml)

文件职责：

- 定义网关默认端口、Nacos、Redis、JWT 和限流相关基础配置

关键行为：

- 默认端口是 `8080`
- 导入 `shared-common.yaml`、`shared-redis.yaml`、`shared-jwt.yaml` 以及服务级 Nacos 配置
- 定义网关 HTTP client 超时和默认限流参数

依赖关系：

- 被网关配置类和过滤器读取

修改风险：

- JWT、Redis、限流配置改错会直接影响所有公网请求

常见改动入口：

- 调整网关超时
- 调整限流配额
- 切换 Nacos 地址和命名空间

## `auth-service` 的 `application.yml`

文件位置：

- [../../auth-service/src/main/resources/application.yml](../../auth-service/src/main/resources/application.yml)

文件职责：

- 定义认证服务的端口、数据库、Redis、邮件和 JWT 基线配置

关键行为：

- 默认端口是 `8081`
- 导入数据库、Redis、JWT 共享配置
- 定义重置密码 token TTL、前端地址和 Feign 超时

依赖关系：

- 被 `AuthServiceImpl`、Redis 配置、邮件能力和 JWT 工具使用

修改风险：

- JWT 相关配置改错会让登录态全线异常
- 邮件配置错误会影响找回密码

常见改动入口：

- 切换数据库或 Redis
- 调整 token 过期时间
- 调整前端地址或邮件参数

## `content-service` 的 `application.yml`

文件位置：

- [../../content-service/src/main/resources/application.yml](../../content-service/src/main/resources/application.yml)

文件职责：

- 定义内容域端口、数据库、Redis、RabbitMQ 和草稿缓存参数

关键行为：

- 默认端口是 `8082`
- 导入数据库和 Redis 共享配置
- 定义提审冷却时间、草稿缓存 TTL、草稿刷回间隔、事件发布/重试参数

依赖关系：

- 被 `ArticleServiceImpl`、`DraftServiceImpl`、`DraftFlushTask` 和内容域 MQ 组件使用

修改风险：

- 草稿 TTL 或刷回频率改错，会影响用户保存体验和最终一致性

常见改动入口：

- 调整草稿缓存策略
- 调整事件发布与重试参数

## `review-service` 的 `application.yml`

文件位置：

- [../../review-service/src/main/resources/application.yml](../../review-service/src/main/resources/application.yml)

文件职责：

- 定义审核域端口、数据库、RabbitMQ 和事件消费参数

关键行为：

- 默认端口是 `8083`
- 导入数据库共享配置和服务级配置
- 定义审核域 Feign 与事件重试参数

依赖关系：

- 被审核任务投影、审核决定事件处理和 MQ 组件使用

修改风险：

- MQ 参数错误会导致审核任务生成或审核决定事件异常

常见改动入口：

- 调整审核域事件重试策略

## `search-service` 的 `application.yml`

文件位置：

- [../../search-service/src/main/resources/application.yml](../../search-service/src/main/resources/application.yml)

文件职责：

- 定义搜索服务端口、数据库、RabbitMQ、Elasticsearch 和事件参数

关键行为：

- 默认端口是 `8084`
- 导入数据库共享配置
- 定义 Elasticsearch 连接地址

依赖关系：

- 被搜索查询、索引同步、启动回填和 MQ 消费逻辑使用

修改风险：

- ES 地址错误会直接让搜索服务启动失败或查询不可用

常见改动入口：

- 切换 ES 地址
- 调整搜索事件重试策略

## `file-service` 的 `application.yml`

文件位置：

- [../../file-service/src/main/resources/application.yml](../../file-service/src/main/resources/application.yml)

文件职责：

- 定义文件服务端口和存储参数

关键行为：

- 默认端口是 `8085`
- 定义上传目录、访问前缀、允许类型和最大文件大小

依赖关系：

- 被 `StorageConfig`、`UploadServiceImpl` 和 `WebMvcConfig` 使用

修改风险：

- 上传目录或访问前缀改错，会出现“能上传但不能访问”或“访问到了错误目录”

常见改动入口：

- 调整上传大小限制
- 调整文件存储位置

## `notification-service` 的 `application.yml`

文件位置：

- [../../notification-service/src/main/resources/application.yml](../../notification-service/src/main/resources/application.yml)

文件职责：

- 定义通知服务端口、数据库、RabbitMQ 和事件参数

关键行为：

- 默认端口是 `8086`
- 导入数据库共享配置
- 定义事件消费重试参数

依赖关系：

- 被通知监听器和通知事件服务使用

修改风险：

- MQ 配置错误会导致通知漏消费

常见改动入口：

- 调整通知消费重试策略

## 配置优先级怎么理解

当前仓库配置来源大致按这个顺序叠加：

- 模块自己的 `application.yml`
- `spring.config.import` 导入的 Nacos 配置
- 环境变量覆盖

理解重点：

- `application.yml` 提供默认值和本地开发基线
- Nacos 用来承接环境化配置
- 环境变量适合在服务器启动脚本层做最后覆盖

## 为什么正式基线是 `java -jar`

当前仓库已经把 Linux 发布基线写在 [scripts/run-service.sh](../../scripts/run-service.sh) 和 [docs/03-runtime-and-delivery/03-release-runbook.md](../../03-runtime-and-delivery/03-release-runbook.md) 里。正式基线选择 `java -jar` 的原因是：

- 当前仓库已经具备多 Jar 服务化运行方式
- 发布脚本、环境变量和 Nacos 约定都围绕这个基线整理
- 业务服务并没有默认提供容器化发布链路作为主路径

`spring-boot:run` 仍然可以作为本地开发手段，但它不是当前仓库定义出来的正式交付方式。
