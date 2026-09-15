# S4 业务补齐：实现与隔离验收

> semi-overt · 文档整理 2026-09-15 · 阶段资料：保留原日期、契约与验收范围；不代表当前版本、整站或生产验收。 [文档中心](../README.md)

日期：2026-09-11。结论：四个完整工作包已实施，主代理完成方向审查、真实隔离验收和全仓回归。仅本地微服务演示；不代表 S5 全站/迁移验收、S6 交付工程或生产切换。

## 1. 四包结果

| 顺序 | 工作包 | 完成内容 | 主代理验收 |
|---|---|---|---|
| 1 | 用户资料、写作日历与首页 | username/ID、资料字段局部更新、分页/limit、公共隐私、三年 updated_at 日历、用户级首页曝光 | 包1及依赖 77 测试通过；真实43断言含七服务启动/登录；Edge真实资料/首页/日历 |
| 2 | 搜索 | 正文HTML/Markdown清洗、相关性排序、可选FULLTEXT、稳定分页、用户搜索、完整卡片字段、错误不伪装空结果 | 包2及依赖40测试通过；真实21断言；实际前端三条结果及字段 |
| 3 | 文件上传与安全校验 | 真实图片格式/解码、5MiB限制、像素/尺寸/并发、local路径/碰撞保护、不信任oldUrl、可选Cloudinary | file模块31测试通过；真实9断言；浏览器上传封面→保存资料→静态读取 |
| 4 | 通知及历史兼容 | 受保护GET列表、用户隔离、新通知中文语义、旧S3英文/旧单体NULL历史兼容、重复决定幂等 | notification模块13测试通过；真实19断言；MySQL/Rabbit重复事件回执 |

上述按模块/工作包统计存在依赖重叠，不能相加作为全仓测试总数。四包真实断言合计92；包1数字含公共启动和登录检查。

## 2. 最终全仓回归与不可变产物

- **完整 reactor verify：220 tests / 0 failures / 0 errors / 0 skipped**。
- 完成：2026-09-11 **16:26:20 +08:00**。
- 日志：`D:\works\semi-overt-backend\.runtime\s4\verify-20260911-162206.log`。
- 逐suite回执及总数：同名 `.json`，统计本次更新的Surefire XML，不计算历史残留报告。
- 构建输出：`D:\works\semi-overt-backend\.runtime\s4\final-verify-build`。
- 最后使用这些 verify 产物分别复制到两次独立运行目录，核验 SHA256 后启动，避免后续构建改写正在运行的jar。

### S4 七服务真实验收

- **92/92 断言通过**，2026-09-11 **16:30:30 +08:00**。
- 回执：`.runtime/s4/accept-20260911-162835/receipt.json`。
- 产物哈希：该目录 `artifacts.json`。
- 脱敏HTTP回执：`http-receipts.jsonl`（登录响应不保存token）。
- 包级响应：`package1-wire.json` 至 `package4-wire.json`。
- 全部使用真实MySQL、Redis、RabbitMQ、Nacos和七个正式服务jar，无数据库/服务发现/消息队列mock。
- 每次随机 `s4_accept_*` schema、Nacos namespace、Rabbit vhost及独立Redis容器；Java端口20080–20086，上传目录隔离于run目录。

### S3 跨服务故障回归

- **71/71 断言通过**，2026-09-11 **16:30:10 +08:00**。
- 回执：`.runtime/s3/accept-20260911-162753/receipt.json`。
- 日志：`.runtime/s4/s3-regression.log`。
- 使用同一组最终verify jar的独立快照；随机S3库/namespace/vhost及独立Redis，Java端口19080–19086。
- 覆盖原S3并发草稿、状态代次、审核权限、唯一决定、重复/乱序事件、丢失任务恢复、Content停机同key恢复、100草稿额度及Redis断开时content数据库持久化。
- Redis断开不代表网关/全站可用，网关Redis限流依赖仍存在。

## 3. 前端真实证据

仅使用演示前端 `D:\works\semi-overt-frontend`（基线43f24dd）；不修改生产前端。Vite在15173，显式代理20080。

- `npm run test:auth-session`：14/14。
- `npm run test:s3-frontend`：15/15。
- `npm run build`：通过。
- 真实网关响应经过当前 `adapters.ts` 的检查：`.runtime/s4/frontend-real-contract.log`。
- Edge截图目录：`.runtime/s4/browser/`。

| 文件 | 证据 |
|---|---|
| `01-public-profile.png` | 访客公开资料及日历 |
| `02-authenticated-home.png` | 浏览器真实登录后的首页 |
| `03-owner-calendar.png` | 本人两天创作日历，含私有统计；访客只能看到公开天 |
| `04-search-results.png` | 真实搜索列表3条结果，精确标题→前缀→正文，含作者/字数 |
| `05-uploaded-cover-profile.png` | 浏览器上传封面并保存后的资料页 |
| `cover-save-receipt.json` | 资料重新读取到的本地上传URL |
| `upload-static-receipt.json` | 该URL GET 200、image/png、68字节 |

浏览器证据来自 `.runtime/s4/accept-20260911-155100` 和 `accept-20260911-161509` 的合成数据；最终verify jar另外重复92项接口断言。页面原fixture封面 `example.invalid` 无法加载，后通过真实上传替换；初次匿名refresh401为正常未登录探测。既有Vue警告不作为业务成功证据，也未扩大为UI重构。

## 4. 审查修正与历史边界

详细过程见 `s4-worker-review.md`。重要修正：

- 访客总字数不再泄露私有文章；SQL直接聚合，不无界加载作者全部文章。
- 资料更新只写四个资料字段，不回写role/password/sessionVersion。
- 三年边界以当天00:00计算；MySQL测试使用微秒精度，避免纳秒fixture舍入到次日。
- 首页不吞曝光写失败，不再全局修改last_featured_at。
- 搜索regex里的`#{1,6}`被MyBatis误作参数，主代理改成`[#]{1,6}`并用真实MyBatis/MySQL回归；基础SQL失败不是200空列表。
- FULLTEXT是可选相关性辅助，不用token命中门槛丢失LIKE原本的结果；内部受token保护的能力查询验证开关和实际索引状态。
- oldUrl不是所有权证明，后端不会自动删除其目标；演示前端只修正对应误导注释。
- 新中文通知兼容已有明确S3英文决定记录重放，不重写原文；未知NULL关联不猜测绑定。

## 5. 明确未完成或不在本阶段的事项

- 没有新增V5；复用S1/S3已具备的表/约束，V1–V4和单体参考schema未改写。
- 没有真实Cloudinary网络/凭据验收；已完成BasicAuth multipart本地模拟服务、返回URL校验、HTTPS/禁止redirect和缺配置失败测试。
- OSS兼容保留，R2不是必需依赖。
- EMAIL仍为PENDING且sent_at NULL，没有邮件worker，不声称真实邮件已发送。
- 没有导入生产/脱敏单体业务数据，没有声称S5迁移对账或全站能力均通过；SMTP/Turnstile真实提供方、容量、生产TLS和部署回滚仍不算本次验收。
- 验收结束时已有S3与S5未提交改动完整保留，未执行生产部署。2026-09-11按用户要求将本机环境与S3/S4成果分组纳入本地提交；测试数据、凭据、日志和截图仍留在忽略目录，不推送远端。提交前确认代码与验收清单一致，只有文档交付状态/前端路径更新。

## 6. 复验入口与清理

```powershell
# PowerShell 7；先保证隔离中间件和本机忽略的local.env可用，不打印凭据。
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s4-verify.ps1
pwsh -NoProfile -File D:\works\semi-overt-backend\scripts\s4-acceptance.ps1
```

`s4-acceptance.ps1`默认构建后创建随机测试环境，结束只停止它拥有的Java/Redis，保留证据/数据库。`-SkipBuild`必须指向明确验证过的`-BuildRoot`，不能拿IDE污染的旧target冒充当前产物。`-KeepServices`用于浏览器检查；按run回执使用`s4-stop-acceptance.ps1`核对PID命令行/前端创建时间后停止。

本次最终验证运行均已停止；保留随机测试数据库、消息空间、上传和截图供审查。既有Aearn、单体和S5中间件未被删除；没有执行Docker volume清理或删除原工作区文件。
