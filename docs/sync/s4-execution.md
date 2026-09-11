# S4 业务同步执行记录

日期：2026-09-11。状态：四包实施、主代理审查与隔离验收已完成。最终依据见`s4-acceptance.md`。仅针对本地微服务演示，不授权生产变更。

## 范围与顺序

1. 用户资料、写作日历与首页。
2. 搜索。
3. 文件上传与安全校验。
4. 通知及历史兼容。

四个工作包分别由 gpt-5.6-luna / max 子代理实施；主代理统一审查契约、数据所有权、迁移、网关和集成验收。独立模块可并行实现，但以上述完整工作包顺序向用户汇报，不用短切片冒充完成。

## 仓库和起点

- 可写：`D:\works\semi-overt-backend`、配套演示前端 `D:\works\semi-overt-frontend`。
- 单体 `D:\works\semi-overt-springboot` 只读作语义来源；生产前端 `D:\works\semi-overt` 不改动。
- 后端起点 HEAD 为 `e8286c6`，S3 和本地 S5 环境已有未提交工作，不重置、不广泛暂存。
- 原始 HEAD、状态、tracked/staged patch 和 111 个现存脏文件的副本及 SHA256 在 `.runtime/s4/baseline/`。这些本地回退材料不提交。
- 前端起点 `43f24dd`，检查时工作区干净。

## 审查门槛

- 对照当前单体源码及演示前端调用，不按旧 AGENTS 的过时说明推断认证和接口。
- content 仍是文章唯一写入方，auth 管用户，notification 管通知；search 只保留已批准的共享数据库只读例外。
- 用户首页曝光不能退化为全局文章轮换；访客列表、统计、日历统一隔离非公开内容。
- 搜索可选 FULLTEXT 不等于独立索引已建成；必须验证无索引退路和正文清洗。
- 上传真实格式、解码、像素、字节和路径约束不可被 MIME/扩展名或 oldUrl 绕过；不重复创造预算权威。
- 通知遵循单体 GET 列表契约，不能凭空扩大为未读中心；PENDING delivery 不等于邮件已发送。
- 后端构建使用独立输出目录，真实测试仅操作随机隔离数据库和本轮拥有的进程/消息空间。
- S4 验收不替代 S5 全站验收、真实云提供方验证、数据导入或生产切换。

## 最终结果

### 包1：用户资料、日历与首页（已通过）

- 主代理独立verify：2026-09-11 16:02:22 +08:00，77 tests / 0 failures / 0 errors / 0 skipped。
- 日志与逐suite统计：`.runtime/s4/verify-20260911-160121.log`、同名JSON；构建输出 `.runtime/s4/pkg1-main-verify`。
- 真实七服务验收：`.runtime/s4/accept-20260911-155100/receipt.json`，43断言通过。
- 访客公开字数2431，不含私有498；公开日历当年2310、本人当年2808；三年前边界按天，MySQL fixture按微秒而非纳秒构造。
- 演示前端实际adapters：`.runtime/s4/pkg1-frontend-contract.log`。
- 真实Edge截图：`.runtime/s4/browser/01-public-profile.png`、`02-authenticated-home.png`、`03-owner-calendar.png`。
- 页面fixture封面为不可解析example.invalid测试URL，导致预期资源加载失败，不是业务API失败；未拿无图占位作为上传验收。初次匿名refresh401是未登录探测。
- 用户已收到第1完整包汇报，未将后续包的预检结果提前宣称完成。

### 包2–4与全仓收尾（已通过）

- 包2主代理独立verify40测试通过（含依赖），MyBatis参数解析错误、FULLTEXT漏召回及基础SQL错误吞掉等由主代理收口修复。
- 包3 file模块31测试、包4 notification模块13测试通过；gateway/file/notification/architecture联合104测试通过（含依赖）。
- 最终完整reactor：220测试，0失败/0错误/0跳过。
- 最终verify jar的S4真实92断言、S3真实71故障回归全部通过。
- 按1→2→3→4完整包顺序向用户报告；没有将预检/编译当作完整包通过。
- 产物回执、截图、复验命令、提供方/部署边界集中于`s4-acceptance.md`。
