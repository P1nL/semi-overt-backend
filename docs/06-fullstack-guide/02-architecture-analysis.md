# semi-overt 架构设计分析

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 为什么区分真源和投影

用户、会话、请求预算由 Auth 管；文章状态和版本由 Content 管；Review 管审核命令、分配与日志。Search/Notification 根据事件生成视图，不反过来决定文章状态。这样能定位责任，但引入最终一致性和故障恢复成本。

## 为什么不是“JWT 有效就放行”

设备会话需要支持撤销、刷新轮换和多实例一致性。Gateway 通过 Auth 权威接口校验，不维护另一套独立会话真源。认证或预算依赖故障时返回明确失败，不能静默允许写请求。

## 为什么审核有两个状态体系

文章业务状态属于 Content；FINAL/PROCESSING/CONFLICT 属于审核决定处理。Review 发起命令后可能收到超时，但 Content 已完成写入，因此需要持久化 decisionId 和查询结果。换 key 重试可能制造新决定；同 key 恢复才能保持原操作身份。

## 为什么需要版本、Outbox 和 Inbox

- version CAS 解决同一文章并发写；submissionId 区分不同提审轮次。
- Outbox 让业务写入与待发事件原子提交；发布必须等待确认，不能把发送调用返回当成投递完成。
- Inbox 与业务写入共同提交，避免重复投递产生多条通知或日志。
- 单调版本与墓碑避免乱序事件重新打开旧任务。

这些机制解决的问题不同，不能用一个“幂等”标签替代所有检查。实际行为以 [S3 契约](../sync/s3-contract.md) 和当前实现为准。

## 前端需要承担什么

保存与审核发送已加载的基线；未知结果保留原请求身份；503 不伪装空数据或永久退出；刷新请求合并但业务写入不无限重试。AI 润色预览不写数据库，应用前确认编辑器仍匹配原快照。

## 工程边界

共享代码放 platform-*，Feign 放 *-contract；数据库迁移独立于业务启动。源码、运行 JAR、镜像和历史回执是四种不同证据。演示的多实例故障通过不等于生产容量、数据迁移或全量切换已验证。

入口：[架构约束](../02-architecture/04-architecture-constraints.md)、[发布说明](../03-development-and-operations/03-release-and-deployment.md)。旧版详细分析见[归档](../archive/fullstack-before-2026-09-15/README.md)。
