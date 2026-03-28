# 分布式改造文档

这组文档面向当前这个单体 Spring Boot 内容平台的后续分布式改造，目标不是泛泛讲微服务概念，而是把“为什么这样拆、先做什么、接口怎么定、数据怎么迁、线程怎么开”固定成一套可直接执行的方案。

读完这组文档后，你应该能回答这些问题：

- 当前单体为什么适合拆成 `gateway/auth/content/review/notification/search/file`
- 为什么这次改造要坚持“先拆服务，后拆库”
- 哪些外部接口必须保持 `/api/v1/**` 兼容
- 哪些内部接口已经固定，不能在线程里随意改
- RabbitMQ、事件表、通知、搜索、审核任务应该怎么挂到主链路上
- 如果要并行开线程，每条线程应该带什么上下文和约束

建议阅读顺序：

1. [01 分布式改造总体设计文档](./01-overall-distributed-design.md)
2. [02 服务拆分与迁移实施文档](./02-service-split-and-migration-plan.md)
3. [03 数据库与事件模型设计文档](./03-database-and-event-design.md)
4. [04 分布式改造新线程提示词包](./04-thread-prompt-pack.md)

各文档职责：

- `01`：讲清总体目标、技术选型、服务清单、核心链路、安全与一致性设计
- `02`：讲清服务边界、外部 API 映射、内部接口、迁移顺序和风险点
- `03`：讲清表归属、事件模型、队列设计、幂等策略和拆库演进
- `04`：把改造任务拆成 6 条新线程，提供可直接复制的完整提示词

优先要记住的固定约束：

- 先拆服务，后拆库
- 外部 API 保持 `/api/v1/**` 兼容
- 不引入 Seata，不做强一致分布式事务
- 文章状态真源在 `content-service`
- 固定内部接口边界：
  - `POST /internal/users/batch`
  - `GET /internal/articles/{id}/review-snapshot`
  - `POST /internal/articles/{id}/apply-review-result`
  - `GET /internal/reviews/articles/{id}/latest`
- 固定内部请求头：
  - `X-User-Id`
  - `X-Username`
  - `X-User-Role`
  - `X-Trace-Id`

如果你是准备开新线程，推荐先看：

- 先看 [02 服务拆分与迁移实施文档](./02-service-split-and-migration-plan.md)，确认自己负责哪条服务边界
- 再看 [03 数据库与事件模型设计文档](./03-database-and-event-design.md)，确认表和事件归属
- 最后直接使用 [04 分布式改造新线程提示词包](./04-thread-prompt-pack.md) 中对应线程的提示词

如果你是准备开始编码，推荐执行顺序是：

1. 基础设施线程
2. 认证与网关线程
3. 内容域线程
4. 审核域线程
5. 事件与派生能力线程
6. 交付与运维线程

补充说明：

- 这组文档默认服务拆分仍在同一个仓库中推进，采用 Maven 多模块
- 当前仓库已经落地基础 `search-service` + Elasticsearch 链路，后续重点是继续增强而不是从零补齐
- 当前 Redis 中的草稿正文缓存仍然是关键事实，拆分后只归 `content-service` 所有
