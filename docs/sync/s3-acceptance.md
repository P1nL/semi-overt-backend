# S3 状态核心：实现与隔离验收

日期：2026-09-10。范围：S3 内容状态/草稿 CAS、审核分配和命令、版本化事件、Outbox/Inbox、相应前端契约。不是 S4/S5 全站验收或生产切换授权。

## 实现结论

1. **草稿数据库真源**：移除 Redis 无版本草稿读写/flush，null 不改、空串清空；DRAFT/RETURNED 可保存，版本 CAS、成功递增且私有；服务器生成时间与派生统计。100 篇草稿箱配额使用 content 自有 author-lock 表串行化创建。
2. **状态代次**：提审生成 submissionId、递增 version/submitCount；取消、作者删除（含已发布）、管理员删除均由 content CAS 更新并在同事务写版本化事件，不同步跨服务删任务。
3. **审核权威**：review 锁定任务并提交 command+outbox 后才调用 content；content 唯一写文章，decisionId 绑定完整 payload。FINAL 后才写最终日志/返回成功；超时 503+PROCESSING，同 key 查询/重试可恢复，不把入队或 202 当通过。
4. **审核权限/防旧页面**：确定性分配、排除作者；非分配管理员不能读待审正文/决定。前端传 submissionId+expectedVersion，旧 S1 页面不能审核新 S2。缺失基线的旧客户端仅保留兼容语义。
5. **可靠事件**：MySQL 时钟 lease/fencing，逐条 claim 后 confirm+mandatory return；Inbox 与副作用同事务，commit 后 ack。旧代次事件不能覆盖新任务；任务保留 tombstone。日志和通知以 decisionId 唯一。
6. **恢复对账**：PROCESSING、已有任务、PENDING 无任务分别有界 keyset 轮扫，通过权威内部 API 校对，内部回执和日志可检查结果，不由 review 直接读写 articles。
7. **前端**：详情/保存/提审/取消版本贯通，409 保留本地输入；审核同 key 持久化、有界查询、仅权威 FINAL 更新 UI。移除根据 lastSubmittedAt 推测的 30 分钟冷却，只使用明确服务端 429 提示。

冻结合同：`D:\works\semi-overt-backend\docs\sync\s3-contract.md`。
主代理审查及纠偏：`D:\works\semi-overt-backend\docs\sync\s3-worker-review.md`。

## 后端测试

主代理以独立 Maven 输出目录运行完整 reactor verify，避免 VS Code/JDT 和 Maven 并行覆盖 target 字节码。统计来自 Surefire XML，不是 Test.java 文件数。

- 首次完整通过：**171 tests / 0 failures / 0 errors / 0 skipped**，2026-09-10 17:50:42 +08:00。
- 日志：`D:\works\semi-overt-backend\.runtime\s3\full-verify.log`。
- 最终回归：**171 tests / 0 failures / 0 errors / 0 skipped**，2026-09-10 **18:05:26 +08:00**；日志 `D:\works\semi-overt-backend\.runtime\s3\final-verify.log`。
- XML：`D:\works\semi-overt-backend\.runtime\s3\verify-build\<module>\surefire-reports`。

真实测试重点：content MySQL 7 项（同版本保存/100 配额/作者删已发布/同 key 及不同 key 决定/失败 key 不堵合法决定）；review MySQL 3 项（真实 NULL 清理、旧页面/相反 claim 并发、幂等与冲突日志）；events MySQL/Rabbit 联合 14 项；notification MySQL 2 项；保留 S1/S2 真实 MySQL 回归。

协议单测中的模拟 correlated nack/confirm timeout 与真实 Rabbit 故障证据分开：真实 Rabbit 覆盖 mandatory return、confirm 后持久 PUBLISHED、失败保留 retry、提交后 ack 前物理断连重投、DLQ 重放和毒消息边界。

## 七服务真实联调

- 脚本：`D:\works\semi-overt-backend\scripts\s3-acceptance.ps1`。
- 使用真实 MySQL、Redis、RabbitMQ、Nacos 和七个正式应用 jar；没有服务发现/消息队列/DB mock。
- 每次独立 `s3_accept_*` 数据库、Nacos namespace、Rabbit vhost 和 Redis 容器，Java 端口 19080–19086，不复用 S5 业务库或单体数据。
- jar 复制到每次 run 的 `jars` 并核验 SHA256；后续子代理构建不会改写正在跑的 artifact。
- **71 项断言全部通过**：`D:\works\semi-overt-backend\.runtime\s3\accept-20260910-172940\receipt.json`。
- 包括草稿 CAS/null/清空/15001 拒绝；分配/隐私；只有 Content 最终成功才返回 200；同 key 重试及篡改拒绝；旧页面拒新代次；旧取消事件双重放不关新任务；相反决定并发；日志通知唯一；自审拒绝；删除 tombstone；人为丢任务后修复；停止 Content 得到 503 后重启同 key 恢复；100 配额；Redis 断开时 content 直连保存仍成功。
- Redis 断开测试仅证明 content 持久化与缓存解耦；网关附加 Redis 限流仍依赖 Redis，不声称 Redis 故障下全站可用。

## 真实浏览器证据

前端最终验证：`npm run test:s3-frontend` **15/15**、`npm run test:auth-session` **14/14**、`npm run build` 通过。日志位于 `.runtime/s3/frontend-tests.log`、`frontend-auth-tests.log`、`frontend-build.log`。

当前前端 Vite 明确代理至 19080，使用独立测试账号，未使用生产用户。

| 证据 | 内容 |
|---|---|
| `.runtime/s3/browser/01-editor-saved.png` | 真实编辑器保存，文章 105 |
| `.runtime/s3/browser/02-editor-pending.png` | 提审后禁用编辑并显示待审核 |
| `.runtime/s3/browser/03-review-assigned.png` | 实际分配的管理员读取正文并可操作 |
| `.runtime/s3/browser/04-review-final.png` | 审核后操作区禁用；日志文字由浏览器 snapshot 与 DB 回执佐证，截图视口下方未完整显示日志 |
| `.runtime/s3/browser/05-cancel-edit-resubmit.png` | 文章 106 取消、编辑保存、立即重提成功 |
| `.runtime/s3/browser/review-request.json` | decisionId + submissionId + expectedVersion=3 |
| `.runtime/s3/browser/review-response.json` | HTTP 200、FINAL、APPROVED、服务端 updatedAt |
| `.runtime/s3/browser/authority-receipt.tsv` | DB 文章版本4、同 decision FINAL、审核日志1条/通知1条 |
| `.runtime/s3/browser/resubmit-authority.tsv` | 文章106 PENDING、version7、submitCount2、新 submissionId |

以上路径均以 `D:\works\semi-overt-backend` 为根。浏览器对象和内容均为测试 fixture；首个空会话 refresh 401 属正常未登录探测。页面背景出现既有 `[object Promise]` 文本，未作为 S3 业务通过证明，也未混入此轮 UI 重构。

## 运行方式

```powershell
# 可选 MavenSettings 是本机已有代理配置，不写生产凭据
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s3-acceptance.ps1
```

默认构建、隔离迁移、运行断言后只停止本脚本拥有的 Java/Redis，保留 run/数据库/消息空间用于检查。`-SkipBuild` 只适用于明确已完成 `.runtime/s3/build` 构建；`-KeepServices -SkipRedisFault` 供后续浏览器检查，不作为 Redis 故障覆盖替代。

完整 verify 环境：S1/S2/S3_MYSQL_URL 均为 `jdbc:mysql://127.0.0.1:13306/`；password 从忽略目录 local.env 读取，S3_RABBIT_HOST/PORT/USERNAME/PASSWORD 对应隔离 Rabbit。使用 `-Ds3.buildRoot=D:/works/semi-overt-backend/.runtime/s3/verify-build`，不要引用受 IDE 增量构建污染的旧 target。

## 迁移、旧数据与交付边界

- V1–V3 保持不改；V4 同步添加于批准的 legacy/monolith 路由。保留原文、版本、旧日志；历史 submissionId 确定性回填。
- 旧事件协议不能直接当新审核命令重放。V4 前检查旧 PENDING/DEAD Outbox 或未成功 Inbox 并拒绝自动切换；运维仍须停写并排空/对账旧 broker 队列。仅 PUBLISHED 状态不能证明 broker 已排空。
- MySQL 部分 DDL/字段或约束漂移 fail-closed，不自动 repair/baseline。
- 历史 Redis 草稿不读、不 flush、不删除；若存在比 DB 新的遗留缓存，需单独批准的只读导出与人工版本策略，不能自动覆盖 DB。
- 未运行生产迁移、未导入单体数据、未配置真实 SMTP/Turnstile/Cloudinary。没有把 S4/S5 全站测试、生产 TLS、容量/灰度/回滚算作本次通过。
- 本轮变更未暂存、未提交、未推送。原有 S5 compose/launcher/docs、前端 Vite proxy 与 dev-frontend 文件保留，不能随 S3 广泛暂存。
- 验收结束已停止本次 19080–19086 Java、专属测试 Redis 和 15173 Vite，保留数据库、截图、日志和权威回执。S5 四个中间件、原有单体及 Aearn 容器保持运行。清理回执 `.runtime/s3/cleanup-receipt.json`。
