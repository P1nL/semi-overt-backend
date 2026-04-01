# 术语表

适合谁看：第一次接触仓库缩写、事件名、模块名的人。  
读完能解决什么问题：快速理解文档和代码里反复出现的术语。

## 业务术语

- 真源：某类业务事实的最终权威来源
- 派生视图：由真源变化异步生成的只读或投影结果
- 提审：作者把草稿提交给审核链路
- 审核决定：审核员对文章做通过、退回或拒绝等动作

## 技术术语

- Gateway：对外统一入口，当前是 `gateway-service`
- Contract 模块：放 Feign client 和跨服务 HTTP 契约的模块
- Outbox：先写本地事件表，再异步发布到消息队列的做法
- DLQ：死信队列
- TraceId：贯穿请求链路的追踪 ID，对应 `X-Trace-Id`

## 当前固定名词

- 内部身份头：`X-User-Id`、`X-Username`、`X-User-Role`
- 主队列：`article.submitted.review`、`review.decided.content`、`article.status.changed.*`
- 本地运行时目录：`.codex-runtime`
- Linux 运行时目录：`.runtime`
