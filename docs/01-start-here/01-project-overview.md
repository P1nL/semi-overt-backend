# 项目概览

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

适合谁看：所有首次接手仓库的人。  
读完能解决什么问题：建立系统全局心智模型，知道当前真实实现、主要能力、模块构成和运行基线。

## 一句话说明

`semi-overt` 是一个围绕内容创作、审核、通知和搜索链路构建的后端示例仓库。

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
- [db-migration](../../db-migration)：Flyway 数据库迁移
- [architecture-tests](../../architecture-tests)

需要特别记住：

- 根目录旧单体 `src/` 已移除，不再作为当前事实来源
- 本地数据库初始化脚本位于 [deploy/sql/init.sql](../../deploy/sql/init.sql)
- 当前对外入口统一是 `gateway-service`

## 系统为什么拆成这样

- `gateway-service`：统一公网入口，通过 Auth 校验设备会话，处理 TraceId、可信身份头、预算/限流和路由
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

- S5 本机联调：scripts/s5-env.ps1，网关 18080。
- 全 Docker 演示/离线交付：scripts/docker-demo.ps1，应用 18000。
- 传统 8080 开发入口与 Linux/SAE 示例继续保留，但不代表生产部署状态。
- 详细步骤见[首次运行](04-ten-minute-run.md)和[发布说明](../03-development-and-operations/03-release-and-deployment.md)。

## 读完后应该记住什么

- 当前仓库已经是多模块微服务结构
- 文章状态以 `content-service` 为准
- 搜索和通知是事件驱动的派生结果
- 运行与发布入口已经有固定脚本，不需要从零拼装
