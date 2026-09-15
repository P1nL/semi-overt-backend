# 端口、依赖与队列清单

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 服务默认端口

| 服务 | 传统本机 | S5 本机 |
| --- | --- | --- |
| Gateway | 8080 | 18080 |
| Auth | 8081 | 18081 |
| Content | 8082 | 18082 |
| Review | 8083 | 18083 |
| Search | 8084 | 18084 |
| File | 8085 | 18085 |
| Notification | 8086 | 18086 |

## 中间件宿主机映射

| 依赖 | 传统 Compose | S5 Compose |
| --- | --- | --- |
| MySQL | 3306 | 13306 |
| Redis | 6379 | 16379 |
| Nacos HTTP / gRPC | 8848 / 9848 | 18848 / 19848 |
| RabbitMQ AMQP / 管理 | 5672 / 15672 | 15673 / 15683 |
| Mailpit SMTP / UI | 以所用配置为准 | 11025 / 18025 |

全 Docker 模式：前端 18000、网关 18080、Mailpit 18025；Adminer 18026 仅可选启用。其 MySQL/Redis/RabbitMQ/Nacos 默认没有宿主机映射，不使用 S5 端口。S5 与 Docker 还可能争用邮件和工具端口；以实际 Compose 和 docker ps 为准。

## 服务依赖

Auth 依赖数据库会话/预算及邮件、Redis；Gateway 依赖 Auth 权威接口、Redis 限流和 Nacos；Content、Review、Search、Notification 的持久化与事件链路依赖 MySQL/RabbitMQ；Content 润色另需 Redis 与外部模型。File 依赖所选存储及上传预算链路。各服务注册/发现配置见 application.yml。

## 事件拓扑

[EventConstants](../../platform-kernel/src/main/java/com/platform/kernel/constant/EventConstants.java) 定义五个主队列：

- article.submitted.review：审核任务投影。
- review.decided.content：内容应用决定/重放。
- article.status.changed.review：审核任务状态收敛。
- article.status.changed.notification：通知。
- article.status.changed.search：搜索。

基础事件交换机为 article.submitted.exchange、review.decided.exchange、article.status.changed.exchange。每个主队列还有 .main.exchange、.retry.exchange、.dlq.exchange 及 .retry/.dlq 队列。队列存在不等于消费成功，需结合 Outbox、Inbox、业务记录验证。

## 上传目录

静态前缀默认 /static/uploads。file-service 的源码默认目录仍含历史值 E:/nowdata/app/uploads；这不是项目展示名，也不适合作为新机器默认部署路径。使用 STORAGE_UPLOAD_PATH 指定本机目录或容器挂载路径，并验证持久化和写权限。不要只改文档路径而漏改运行配置。
