# 内部协作接口

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

内部接口不是第二套公网 API。网关不开放 /internal/**；业务服务也需验证内部令牌与身份。不能以“路径叫 internal”代替访问控制。

| 权威服务 | 契约 / 入口 | 用途 |
| --- | --- | --- |
| Auth | AuthUserQueryClient、InternalUserController | 批量用户摘要、可参与审核的管理员 |
| Auth | POST /internal/auth/session/validate | 持久化设备会话校验 |
| Auth | POST /internal/auth/budget/consume | 跨实例共享请求预算 |
| Content | ContentReviewClient、InternalArticleController | 审核快照、应用决定、读取决定结果、扫描待审真源 |
| Content | ContentProfileClient | 用户主页文章聚合 |
| Review | ReviewTaskClient | 任务投影维护、按 submissionId 获取分配 |
| Review | ReviewReasonClient | 最近审核信息 |

## 一致性约束

- Feign 声明放在对应 *-contract 模块；实现服务不能互相依赖实现模块或直接查写其他服务业务表。
- 会话和预算调用由 Gateway 的 SessionAuthorityClient 负责；依赖故障应明确失败，不静默放行。
- Content 决定查询 GET /internal/articles/{id}/review-decisions/{decisionId}：不存在不能猜成功。
- Review 请求 Content 前先持久化自己的命令；同步请求和消息重放使用同一个 decisionId。
- Review 扫描 GET /internal/articles/review-pending 发现缺失投影，不通过 Review 直接 JDBC 查询 articles。
- 任务 upsert/remove 必须保留 submissionId 与 articleVersion，避免旧事件覆盖新轮次。

通知和搜索异步派生优先走事件；需要同步权威事实才使用内部 HTTP。具体参数见 [S3 契约](../sync/s3-contract.md) 和源码目录：

- [Auth clients](../../auth-contract/src/main/java/com/platform/contract/auth/client)
- [Content clients](../../content-contract/src/main/java/com/platform/contract/content/client)
- [Review clients](../../review-contract/src/main/java/com/platform/contract/review/client)
