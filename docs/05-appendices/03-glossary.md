# 术语表

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

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
- 传统本机运行时目录：`.codex-runtime`
- S5 运行时目录：`.runtime/s5`
- 全 Docker 私有环境文件：`.runtime/docker-demo.env`

## 会话、并发与交付

- Device session：Auth 数据库中的设备会话权威。
- Access token / refresh Cookie：访问凭证与刷新凭证，不是同一个存储/生命周期。
- version / CAS：用已读版本作为写入前提，避免并发覆盖。
- submissionId：一次提审轮次；decisionId：一次审核决定身份。
- FINAL / PROCESSING / CONFLICT：决定处理状态，不是文章业务状态。
- Inbox：消费者去重和事务一致性记录。
- 墓碑：保留删除/取消后的版本信息，防止旧事件复活投影。
- readiness：服务就绪，不代表业务或模型已验收。
- 离线镜像包：镜像及启动文件，不自动包含数据库、上传文件或外部服务。
