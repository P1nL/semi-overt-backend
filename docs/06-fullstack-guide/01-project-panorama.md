# semi-overt 项目全景

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 仓库边界

本目录讲解当前微服务后端与独立 semi-overt-frontend 演示前端的协作。生产前端 semi-overt 与旧 semi-overt-springboot 单体不属于本轮演示修改范围；项目名相同不表示可以混用运行环境或提交。

## 技术栈与职责

| 层 | 当前仓库使用 | 职责 |
| --- | --- | --- |
| 页面 | Vue 3、TypeScript、Vue Router、Pinia | 页面、导航与客户端状态 |
| 服务端状态 | TanStack Vue Query、Axios | 查询缓存、HTTP 请求、失败处理 |
| 编辑器 | Tiptap、DOMPurify | 富文本编辑与显示清洗 |
| 前端构建 | Vite、Tailwind CSS | 开发代理、构建与样式 |
| 后端 | Java 17、Spring Boot 3.2.3 | 七个业务进程 |
| 服务协作 | Gateway、OpenFeign、Nacos | 公网路由、内部契约、发现和配置 |
| 数据 | MySQL、MyBatis Plus、Flyway | 业务持久化与受控迁移 |
| 异步 | RabbitMQ、Outbox/Inbox | 可恢复事件与投影 |
| 辅助 | Redis | 限流等能力，不是文章或设备会话真源 |
| 模型 | LangChain4j、DeepSeek 配置 | 临时正文润色建议，不直接保存文章 |

版本以各仓库 pom.xml、package.json/锁文件为准；以上不是依赖升级建议。前端 package.json 的旧 name 字段属于未迁移技术元数据，不用作项目展示名称。

## 一次请求与一次事件

页面 → 前端 HTTP 层 → 网关路径归一化 → Auth 会话/预算 → 可信身份注入 → 业务服务 → 数据库。

业务事务 → Outbox → RabbitMQ → 消费者 Inbox + 本地业务事务 → 搜索/通知视图。

前者的 HTTP 成功不保证后者已落地。页面需要正确表达等待、冲突和暂时不可用，而不是把所有错误显示为空列表。

## 阅读路径

1. [模块边界](../02-architecture/01-module-boundaries.md)
2. [登录流程](03-login-flow-trace.md)
3. [内容与审核状态](../02-architecture/02-core-flow-and-state.md)
4. [API 参考](../04-reference/01-api-and-permissions.md)
5. [首次运行](../01-start-here/04-ten-minute-run.md)

更长的旧版技术讲解保留在[归档](../archive/fullstack-before-2026-09-15/README.md)，其中旧路径和登录协议不适用于当前实现。
