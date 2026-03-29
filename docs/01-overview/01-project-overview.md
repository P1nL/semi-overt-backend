# 01 项目概览

## 为什么先读这份文档

这份文档的作用是先帮新接手的后端同学建立全局心智模型：项目是什么、为什么拆成现在这些模块、运行时依赖有哪些、什么才算当前真实实现。

如果你刚进入仓库，先读完这一篇，再去看模块地图和业务链路，会比直接钻源码更高效。

## 项目一句话介绍

`now-demo` 是一个围绕“内容创作 -> 提交审核 -> 审核决定 -> 通知投递 -> 搜索可见”设计的后端仓库。

它的业务目标不是做一个纯博客系统，而是把这些工程问题放到一条完整链路里：

- 用户注册、登录、找回密码
- 作者创建文章、保存草稿、提交审核、取消审核
- 管理员审核文章
- 审核结果触发通知与搜索索引更新
- 首页、分类页、用户主页、公开搜索、图片上传

## 当前真实项目结构

当前运行主实现是父 [pom.xml](../../pom.xml) 管理的 Maven 多模块工程，而不是根目录历史单体 `src/`。

当前模块：

- [gateway-service](../../gateway-service)
- [auth-service](../../auth-service)
- [content-service](../../content-service)
- [review-service](../../review-service)
- [search-service](../../search-service)
- [file-service](../../file-service)
- [notification-service](../../notification-service)
- [common](../../common)

历史残留：

- [src](../../src) 保留了单体阶段的 `init.sql`、旧控制器/服务/Mapper 参考代码
- 它仍有参考价值，但不属于当前服务启动和网关路由的主实现

## 为什么拆成这些服务

### `gateway-service`

把公网入口统一收口，负责鉴权、限流、路由、TraceId 透传。

### `auth-service`

用户与认证真源，负责注册、登录、找回密码和用户摘要输出。

### `content-service`

文章主状态与内容生命周期真源，负责首页、分类、文章、草稿、提审等核心内容行为。

### `review-service`

管理员视角下的审核动作、审核日志、审核任务投影。

### `search-service`

公开搜索接口和 Elasticsearch 搜索投影，只处理已发布文章视图。

### `notification-service`

通知派生数据服务，消费文章状态变化事件并落库通知结果。

### `file-service`

上传入口和静态文件访问映射，隔离文件存储逻辑。

### `common`

共享内部 DTO、事件模型、头协议和公共支持代码。

## 当前技术栈

- Java 17
- Spring Boot 3.2.3
- Spring Cloud Gateway / OpenFeign
- Spring Security
- MyBatis Plus
- MySQL 8
- Redis
- RabbitMQ
- Elasticsearch
- Nacos
- Springdoc OpenAPI

## 当前运行依赖

核心中间件：

- MySQL：主业务数据
- Redis：缓存、黑名单、限流
- RabbitMQ：异步事件链路
- Elasticsearch：公开搜索投影
- Nacos：配置中心与服务发现

运行入口：

- 本地 Windows： [docker-compose.yml](../../docker-compose.yml) + [dev-up.ps1](../../scripts/dev-up.ps1)
- Linux 基线： [run-service.sh](../../scripts/run-service.sh) + `java -jar`
- 可选 Nginx： [deploy/nginx/now-demo.conf](../../deploy/nginx/now-demo.conf)

## 当前对外能力

当前仓库已经具备这些真实能力：

- 注册、登录、找回密码、登出
- 文章创建、草稿保存、提审、取消审核、详情查看
- 审核队列、审核通过/退回/拒绝
- 审核结果触发通知与搜索同步
- 首页、分类页、公开搜索、上传、用户主页

特别要记住：

- 公开搜索已经不是占位接口，而是 `search-service + Elasticsearch`
- 搜索、通知属于派生视图，不是内容真源

## 读完后你应该知道什么

- 当前项目是不是还在跑单体
- 哪些服务是对外入口，哪些是内部支撑
- 为什么文章状态必须以 `content-service` 为准
- 为什么搜索和通知适合走异步投影
