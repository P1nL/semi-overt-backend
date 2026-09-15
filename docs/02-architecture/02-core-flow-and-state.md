# 核心链路与状态流转

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 真源与主链路

content-service 是文章与版本真源；auth-service 是用户、设备会话和请求预算真源；review-service 保存任务投影、审核命令和日志。搜索和通知只是派生视图。

1. 注册/登录创建设备会话；后续请求由网关向 Auth 校验会话。
2. 作者创建文章并保存草稿。数据库直接持久化，使用 version CAS，不依赖 Redis 延迟刷库。
3. 作者提审，形成 submissionId 和新的文章版本，写入同事务 Outbox。
4. Review 投影待办并管理分配；管理员以 decisionId 和已加载的 submissionId/expectedVersion 提交决定。
5. Review 先提交本地命令事务，再通过内部契约请求 Content 应用。Content 校验版本、保存决定结果并更新状态及 Outbox。
6. 只有 Content 权威 FINAL 结果可形成成功审核日志；通知与搜索消费文章状态事件更新视图。

## 草稿并发与审核恢复

- 草稿 null 表示不修改，空字符串表示清空；字数等统计由服务端派生。正文上限和字段校验以 SaveDraftReq/实现为准。
- 同一基线版本并发保存不能都成功；冲突应重新读取，不得无版本覆盖。
- 审核 FINAL、PROCESSING、CONFLICT 是决定处理状态，不是文章业务状态。
- 超时或 503 不等于失败未执行，也不等于成功；保留同一个 decisionId 查询 /decision-status 或重试，不能自动换 key 创建另一决定。
- 新提审有新的 submissionId；旧任务、旧页面和乱序事件不得覆盖新版本。投影保留版本墓碑，避免撤回/删除后被旧事件重建。
- 兼容旧客户端省略基线参数不等于具备防陈旧页面保护；新客户端应发送完整基线。

## API 入口

| 操作 | 路径 |
| --- | --- |
| 创建 | POST /api/v1/articles |
| 保存 | PUT /api/v1/articles/{articleId}/draft |
| 提审 / 撤回 | POST /api/v1/articles/{articleId}/submit、/cancel-review |
| 审核 | POST /api/v1/reviews/{articleId}/decision（兼容 /action） |
| 恢复查询 | GET /api/v1/reviews/{articleId}/decision-status |
| 搜索 | GET /api/v1/search/articles |

## 排查顺序

先确认会话/权限，再核对 Content 记录与版本、Review 命令结果、Outbox 确认、消费者 Inbox/投影、最后看前端缓存。不要只凭 HTTP 200 或队列存在宣布业务完成。

源码入口：[DraftServiceImpl](../../content-service/src/main/java/com/platform/content/service/impl/DraftServiceImpl.java)、[ReviewController](../../review-service/src/main/java/com/platform/review/controller/ReviewController.java)。详细契约见 [S3](../sync/s3-contract.md)。
