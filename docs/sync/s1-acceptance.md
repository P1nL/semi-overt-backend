# S1 基础兼容验收 — 2026-09-09

## 范围
S1 包含路由/时间/响应基础兼容、目标基础 schema 和空库/旧微服务/单体快照迁移演练。
S2 Cookie 会话及注册验证码、S3 文章/审核状态机、S4 业务补齐、S5 全站联调不计入 S1 完成。

## 迁移入口
MIGRATION_MODE（默认 AUTO）：
- AUTO：空库执行 V1/V2/V3；已有合法历史按原路线继续并验证 checksum。
- LEGACY_V1：无 history 且严格匹配旧 V1 的库，经表/列类型/长度/nullability/约束/索引及行级检查后显式 baseline 1。
- LEGACY_V2：无 history 且匹配 V1+V2 的库，经检查后显式 baseline 2。
- MONOLITH：无 history 且匹配冻结的 40edf51 schema，经检查后 baseline 2（S1_MONOLITH），只执行独立 monolith V3，不运行旧 V1/V2。
后续重跑必须 AUTO；不能用 onboarding 模式覆盖历史。模式错误、混合库、失败历史、checksum 漂移、孤儿数据、审核队列不一致均拒绝。

生产使用仍需另行授权；此文不是生产执行命令。现有脚本直接继承 MIGRATION_MODE 环境变量，无默认绕过开关。

## 数据保留
- V1/V2 不变；两条 V3 location 分开且脚本名不同，历史记录固定来源路线。
- 单体 password_hash 重命名为 password，保持原 hash、ID、session_version、token hash、persistent、article version。
- URL/昵称/邮箱等扩宽而非截断；正文变 LONGTEXT。已有 FULLTEXT 索引保留。
- 原审核 assigned_admin_id 保留；旧微服务未分配任务新增 nullable 字段，S3 再做分配，不捏造管理员。
- 历史 review_logs.from_status/to_status、notifications.biz_id 不可推导时 NULL，不猜造历史，不重发旧通知。
- 两来源的历史主键、enum/varchar 和 datetime/timestamp 差异按兼容方式保留，不为了机械一致重写历史。S2/S3 必须按该基础 schema 编写实现。
- 会话表存在不表示旧微服务已支持这些会话；S2 完成前不能接入生产。

## 并发和失败
同一库使用 GET_LOCK 串行入口；所有源检查在创建 history/执行 DDL 前完成。
MySQL DDL 不是整批原子事务。中断后禁止自动 repair/baseline 越过；保留失败库，恢复备份到新库再演练。测试已覆盖模拟部分 DDL 后拒绝，以及重建冻结源快照后的成功路径；这不是生产备份恢复证明。
建议停写再迁移；锁只协调本工具，不阻止其他应用/管理员写库。

## 验收证据
- 隔离 Docker MySQL 8.0.46，随机容器名、随机 loopback 端口、合成数据库。没有接触已有数据库或生产数据。
- 真实 MySQL：空库、legacy V1/V2 显式 onboarding、已有 V2 history、单体含用户/文章/审核/会话样例、重复执行、checksum 篡改、缺 unique、字段缩窄、孤儿记录、锁竞争、FULLTEXT 保留、部分 DDL 恢复路径。
- WebFilter 真实 loopback HTTP 测试：两个前缀、method/query/body 保留。
- Spring Cloud Gateway 真实 WebFlux server + 生产 RouteLocator/GatewayAuthFilter：两个前缀搜索匹配以及审核401；在外发前终止，Redis/Nacos/业务服务未实连，不能当全栈验收。
- 保存/审核响应补 updatedAt 别名，保留 savedAt/reviewedAt；草稿返回 draftVisible=false。只是 wire 兼容，不改变审核异步结果权威边界。
- 日志：D:\works\semi-overt-backend\.runtime\s1\final-verify.log。

## 运行测试
默认 Maven verify 跳过需要数据库的 MySqlMigrationTest。使用隔离 MySQL 后设置：
- S1_MYSQL_URL：jdbc:mysql://127.0.0.1:<随机端口>/（末尾 /，不含库名）
- S1_MYSQL_PASSWORD：隔离实例密码（不打印、不提交）
然后运行 .\mvnw.cmd -T 1 -B -ntp verify。测试只创建随机 s1_* 库；不要将环境变量指向现有业务实例。

## 已知边界
源结构采用保守白名单，未匹配的旧自定义结构拒绝而非自动修复。源数据校验覆盖此次迁移直接依赖的不变量，不替代生产数据审计。
目标 postflight 验证基础不变量，Flyway checksum 不证明每项人工 DDL 未漂移。未来新版本须同时更新目标契约与 history 白名单。
S1 不承诺生产无损回滚、支付服务连通、消息幂等、已修复草稿 Redis flush、审核同步落地或前端版本字段升级。

最终结果：全 reactor verify 成功，109 tests，0 failures，0 errors，0 skipped；其中10项真实MySQL测试已启用。S1阶段验收完成，后续进入S2，不等于网站后端切换完成。

测试容器 semi-sync-s1-92e6f6e354 已停止但保留合成数据供复核；未删除或停止任何其他容器。密码仅保存在忽略目录 .runtime/s1/mysql-password，不纳入Git。
