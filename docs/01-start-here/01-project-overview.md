# 项目概览

适合谁看：所有首次接手仓库的人。  
读完能解决什么问题：建立系统全局心智模型，知道当前真实实现、主要能力、模块构成和运行基线。

## 一句话说明

`now-demo` 是一个围绕内容创作、审核、通知和搜索链路构建的后端示例仓库。

它不是单纯展示某个 CRUD 服务，而是用一条完整业务链路把这些工程问题串起来：

- 用户注册、登录、找回密码
- 作者创建文章、保存草稿、提交审核
- 管理员执行审核动作
- 审核结果触发通知投递与搜索可见
- 首页、分类页、公开搜索、图片上传、用户主页

## 当前真实实现

当前运行主实现是父 [pom.xml](../../pom.xml) 管理的 Maven 多模块工程。

业务服务：

- [gateway-service](../../gateway-service)
- [auth-service](../../auth-service)
- [content-service](../../content-service)
- [review-service](../../review-service)
- [search-service](../../search-service)
- [file-service](../../file-service)
- [notification-service](../../notification-service)

支撑与契约模块：

- [platform-kernel](../../platform-kernel)
- [platform-web-support](../../platform-web-support)
- [platform-events](../../platform-events)
- [auth-contract](../../auth-contract)
- [content-contract](../../content-contract)
- [review-contract](../../review-contract)
- [architecture-tests](../../architecture-tests)

需要特别记住：

- 根目录旧单体 `src/` 已移除，不再作为当前事实来源
- 本地数据库初始化脚本位于 [deploy/sql/init.sql](../../deploy/sql/init.sql)
- 当前对外入口统一是 `gateway-service`

## 系统为什么拆成这样

- `gateway-service`：统一公网入口，做 JWT 校验、TraceId 透传、内部头注入、限流、路由
- `auth-service`：用户与认证真源
- `content-service`：文章主状态真源
- `review-service`：审核任务和审核动作真源
- `search-service`：公开搜索与搜索投影
- `file-service`：上传与静态资源访问
- `notification-service`：通知投影与投递记录
- `platform-*`：共享基础能力
- `*-contract`：跨服务调用契约

## 当前技术栈

- Java 17
- Spring Boot 3.2.3
- Spring Cloud Gateway / OpenFeign
- Spring Security
- MyBatis Plus
- MySQL 8
- Redis
- RabbitMQ
- Nacos
- Springdoc 依赖已纳入父 POM

## 当前运行基线

本地开发：

- 中间件：`docker compose up -d`
- 服务启动：[scripts/dev-up.ps1](../../scripts/dev-up.ps1)
- 冒烟验证：[scripts/smoke-test.ps1](../../scripts/smoke-test.ps1)

Linux 服务器：

- 环境变量样例：[scripts/env/server.env.example](../../scripts/env/server.env.example)
- 单服务启动：[scripts/run-service.sh](../../scripts/run-service.sh)
- 可选外部入口：[deploy/nginx/README.md](../../deploy/nginx/README.md)

## 读完后应该记住什么

- 当前仓库已经是多模块微服务结构
- 文章状态以 `content-service` 为准
- 搜索和通知是事件驱动的派生结果
- 运行与发布入口已经有固定脚本，不需要从零拼装
