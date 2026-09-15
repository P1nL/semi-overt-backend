# semi-overt 阶段契约与历史回执

整理日期：2026-09-15。保留原路径以兼容已有链接和脚本引用；此目录不是当前部署操作入口。

## 阅读方法

- contract、design-adr、session 文档是阶段契约，修改能力前需与源码对照。
- acceptance、receipts、worker-review 是指定日期和范围的记录，不是当前版本重新验收。
- plan、execution 是当时的计划或过程；旧待办不自动代表现在未完成，勾选也不代表生产交付。
- s0-fixtures-2026-09-09.json 是机器可读历史夹具，保持数据原样。

| 阶段 | 设计 / 实现 | 验收 |
| --- | --- | --- |
| S0 | [矩阵](s0-contract-matrix-2026-09-09.md)、[ADR](s0-design-adr-2026-09-09.md) | [回执](s0-execution-receipts.md) |
| S1 | [安全切片](s1b-safety-slice.md)、[迁移预检](s1b-schema-preflight.md) | [验收](s1-acceptance.md) |
| S2 | [持久化](s2-session-persistence.md)、[服务](s2-session-service.md) | [验收](s2-acceptance.md) |
| S3 | [契约](s3-contract.md)、[审查](s3-worker-review.md) | [验收](s3-acceptance.md) |
| S4 | [执行](s4-execution.md)、[审查](s4-worker-review.md) | [验收](s4-acceptance.md) |
| S5 | [环境记录](s5-local-environment.md)、[恢复计划](s5-recovery-plan.md) | [恢复专项](s5-recovery-acceptance.md) |

[原同步总计划](../sync-plan-2026-09-09.md) 保留为历史路线图。S5 恢复专项不代表全站契约、历史数据迁移或生产切换完成。当前操作从[文档中心](../README.md)进入。
