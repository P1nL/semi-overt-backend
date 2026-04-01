# 事件与派生视图

适合谁看：要理解异步链路、RabbitMQ 拓扑、搜索和通知为什么独立存在的人。  
读完能解决什么问题：知道事件从哪里发出、由谁消费、为什么搜索和通知不直接写回业务真源。

## 先看结论

- 事件基础设施统一在 `platform-events`
- 业务真源服务通过事件广播状态变化
- 搜索与通知属于派生视图，按事件异步更新
- 队列有主队列、重试队列和死信队列拓扑

## 事件类型

定义在 [EventConstants.java](../../platform-kernel/src/main/java/com/platform/kernel/constant/EventConstants.java)：

- `ArticleSubmittedEvent`
- `ReviewDecidedEvent`
- `ArticleStatusChangedEvent`

## 队列与消费者

主队列：

- `article.submitted.review`
- `review.decided.content`
- `article.status.changed.review`
- `article.status.changed.notification`
- `article.status.changed.search`

当前明确消费者：

- `review-service:article-submitted-task-projector`
- `review-service:cancel-log-writer`
- `content-service:review-result-applier`
- `notification-service:article-status-notifier`
- `search-service:approved-article-indexer`

## 为什么搜索和通知是派生视图

这样拆的直接好处：

- 内容真源不需要同步承担搜索和通知表现层职责
- 搜索和通知可以独立重放或补偿
- 发布链路与投影链路解耦

代价也很明确：

- 异步链路天然有短暂延迟
- 排查问题时要同时看真源状态和消费进度

## 当前 RabbitMQ 拓扑

`platform-events` 会为每个主队列声明：

- 事件扇出交换机
- 主交换机
- 重试交换机
- 死信交换机
- 主队列
- 重试队列
- 死信队列

重试队列带 TTL，超时后回流到主队列。

## 与冒烟脚本的关系

[scripts/smoke-test.ps1](../../scripts/smoke-test.ps1) 会显式检查这些队列是否存在：

- `article.submitted.review`
- `review.decided.content`
- `article.status.changed.notification`
- `article.status.changed.search`

同时它会等待：

- 通知记录落库
- 通知投递记录生成
- 搜索结果可见

这意味着当前主链路不是“只有 HTTP 成功就算通过”，而是要求异步投影也落地。

## 什么时候应该改事件，而不是改同步接口

- 需求目标是更新通知或搜索呈现，而不是回写文章真源
- 需要跨服务异步传播状态变化
- 需要可重试、可补偿的投影逻辑

如果你要改的是“文章当前状态到底是什么”，优先回到 `content-service`。
