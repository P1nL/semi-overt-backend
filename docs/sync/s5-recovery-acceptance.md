# S5-③ 多实例与故障恢复：专项验收

日期：2026-09-11。**用户只授权③，本专项已完成；不代表S5全量完成。** 未做数据迁移、全站功能扩展、外部提供方或生产切换。

## 1. 基线与结果

- 后端基线 `9778cc4`，演示前端基线 `ff98ba0`；开工时工作树干净。
- 正式服务运行12个独立Java进程：auth/gateway/content/review/notification各2，search/file各1。
- Nacos实际确认5组双实例注册；真实Rabbit管理端确认review/content/notification竞争消费者。
- 专项真实后端断言：**93/93通过**。
- 全仓reactor回归：**224 tests / 0 failures / 0 errors / 0 skipped**，17:41:23 +08:00完成。
- 透明TCP故障代理测试：**2/2通过**。
- Edge真实页面中的5组会话恢复场景全部通过，前端源码未改。

最终运行目录：`D:\works\semi-overt-backend\.runtime\s5-recovery\run-20260911-174334`。

| 文件 | 证据 |
|---|---|
| `receipt.json`、`checks-progress.json` | 93项断言、进程身份、范围标记 |
| `artifacts.json` | 正式jar来源与SHA256，不引用IDE增量target |
| `source-state.json` | 后端和演示前端提交基线 |
| `http-receipts.jsonl` | 请求端口/路径/状态/耗时，不保存token/Cookie/密码 |
| `faults.jsonl`、`proxy-events.log` | 精确停止/断连/恢复动作 |
| `recovery-times.jsonl`、`recovery-window-summary.json` | 恢复时间及计时边界 |
| `load-summary.json`、`instance-memory.json` | 本机小负载和进程RSS |
| `final-outbox.tsv`、`final-inbox.tsv`、`final-queue-state.json` | 数据库与消息最终状态 |
| `browser-receipt.json`、`browser/` | 浏览器协议故障/恢复/双标签页结果及截图 |
| `cleanup.json` | 仅停止本轮拥有的进程和Redis；证据/数据库保留 |

完整回归日志：`.runtime/s4/verify-20260911-173802.log`及同名JSON；构建输出`.runtime/s5-recovery/verify-build`。专项最终运行直接使用该verify产物的快照，验证范围不是重新包装另一个版本。

## 2. 实际覆盖

### 双实例会话、预算和并发写

- A登录，B验证并轮换refresh Cookie，A能校验B刷新后的token。
- A撤销会话后A/B均返回401，滚动重启后持久化状态仍一致。
- 对两个auth实例并发发出20次同维度预算请求：配置额度6，总计6次允许、14次拒绝，数据库计数为6而非12。
- 对两个gateway并发12次同IP搜索：6次200、6次429，Retry-After有效。
- 两个gateway轮流上传同一用户：每日测试额度6，8次请求只有6次写文件、2次429；没有重复计数权威。
- 两个content进程同版本保存：恰好一个200、一个409；数据库版本仅递增一次。

### Rabbit/发布者/消费者故障

- 经透明TCP代理切断本轮应用到真实Rabbit的连接；业务事务和Outbox仍持久化，没有把未确认事件标为PUBLISHED。
- 故障期间停止content A，恢复Rabbit后content B接续发布；恢复后Outbox在约15.650秒收敛。
- 两个review实例使用同decisionId并发提交同一决定，只有一套权威结果。
- 同eventId、新eventId重放，以及停止notification A后的消费者接续，均不新增重复日志/通知/delivery。
- 重启消费者后等待Rabbit管理统计确认两个消费者重新加入；没有把Java readiness等同于消费者已经就绪。

### Content/数据库/Redis恢复

- 所有content实例停止时，审核返回503而非FINAL；PROCESSING命令持久化。重启后通过另一个gateway用同key恢复为唯一最终结果。
- 从停止Content到同key最终恢复约**23.914秒**（含服务重启）；原始细项64ms只表示两个实例ready之后查询确认耗时，不能误当整个恢复窗口。
- MySQL故障只切断本轮代理连接，不停止共享MySQL。两gateway明确5xx、refresh不清Cookie、预算不放行、content不确认写入；所有故障请求均在预设10秒内失败。
- MySQL恢复后原会话有效，失败写入没有改变版本；正常保存恢复，连接恢复确认约4.631秒。
- 自有Redis停止时content直连数据库仍能保存，两个gateway的Redis限流不可静默放行；重启并保持同一端口后约2.283秒恢复。

### 本机小负载

测试前冻结100次公开首页请求、并发4、两个gateway各半、成功率100%、p95<2000ms。

- 实际100/100成功。
- p50：39.60ms；p95：60.97ms；最大：66.46ms。
- 12个Java实例RSS逐项保留；这是Windows本机专项回归结果，不代表生产容量或持久压测。

## 3. 发现并修复的产品问题

**Gateway原RedisRateLimiter在Redis异常时返回allowed=true、remaining=-1，属于fail-open。** 新增测试复现并保留失败报告：`.runtime/s5-recovery/redis-baseline-test.log`。

修复保持原Redis Lua令牌桶，新增`FailClosedRedisRateLimiter`：

- 未知/缺失/负数额度不视为真实许可，返回503。
- Redis调用最长3秒，黑洞和异常都明确失败。
- 正常允许和额度耗尽的拒绝语义不变。
- 保留额度响应元数据用于验证。将includeHeaders关闭会拒绝未知额度，而不会悄悄绕过。

4项新增网关回归覆盖Redis异常、正常允许/拒绝、黑洞超时及缺失元数据；真实两个gateway在Redis停机/恢复中也通过验证。

未修改认证/内容/审核事实源、历史迁移、前端产品逻辑或线上运行配置；没有添加宽松令牌桥、关闭鉴权或放宽状态约束。

## 4. 浏览器证据边界

先在真实演示前端通过UI登录，再使用当前`http.ts`和`authRuntime.ts`：

- 对refresh分别注入网络错误、429、503；三个并发受保护请求只触发一次refresh，全部失败但保留会话。
- 移除注入后，真正调用后端refresh和`/users/me`恢复成功。
- 同一浏览器两个真实标签页并发刷新，通过Web Locks串行修改共享Cookie，均成功。
- 确定性refresh401仍触发实际应用失效处理，内存会话清除。

网络/429/503采用浏览器协议级故障注入，不冒充真实第三方或真实数据库故障；真实数据库/Redis/Rabbit断连另由后端专项验证。没有调用生产站点或外部提供方。

## 5. 不隐瞒的遗留边界

- 最终Outbox为9条PUBLISHED，活跃content/review/notification队列均为0积压；Inbox记录均SUCCESS。
- **停用的搜索事件队列仍积压5条，消费者为0。** 当前search读取共享MySQL，没有启用独立索引消费者。此历史拓扑并未在本专项改写，也未删除其消息；不能宣称所有队列都清空。若长期运行，应另行决定停用队列的保留/拓扑策略，不能把开一个no-op消费者吞消息算修复。
- 测试创建独立schema/namespace/vhost，没有真实单体数据迁移和对账，也没有外部SMTP/Cloudinary/Turnstile验收。
- 不代表进程编排自动重启、生产HA、生产容灾或S6/S7交付完成；本次验证的是明确故障与恢复动作下的应用行为。
- 本轮未暂存、提交或推送。

## 6. 失败记录与复验

失败记录保留，没有删断言掩盖问题：

- 首轮复用函数动态作用域导致脚本根目录为空，修正为显式脚本根变量。
- Rabbit消费者重启后的管理统计短暂仍为1，改为等待可观测的双消费者，而不是减弱为单消费者通过。
- Docker随机HostPort在stop/start后重分配，导致一次恢复测试失败；固定本轮独立映射端口并显式验证重启前后一致。这个问题属于测试环境地址漂移，不伪称为应用恢复缺陷。
- CLI执行沙箱没有URL全局变量，浏览器脚本改为本地前缀校验后通过；未改变应用代码。

```powershell
# PowerShell 7；沿用本机S5隔离中间件，凭据从忽略目录读取、不输出。
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-recovery.ps1
# 如需浏览器复验，先指定-KeepServices，再启动仅指向21080的演示前端：
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-recovery-browser.ps1 -RunDirectory <本轮run目录>
# 浏览器UI登录后通过playwright-cli run-code --filename scripts/s5-browser-recovery.js执行。
# 检查结束只停本轮资源：
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s5-stop-recovery.ps1 -RunDirectory <本轮run目录>
```

所有脚本均拒绝非本轮PID/非隔离路径。日志、合成数据库、消息空间保留；本轮Java/Vite/故障代理/Redis已停止，既有S5中间件、Aearn及单体容器保持原状。
