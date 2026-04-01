# 角色化阅读路径

适合谁看：想快速定位自己该看哪些文档的成员。  
读完能解决什么问题：避免从头翻完整个仓库，按角色找到最短阅读路径。

## 后端开发

建议顺序：

1. [项目概览](./01-project-overview.md)
2. [模块边界](../02-architecture/01-module-boundaries.md)
3. [核心链路与状态流转](../02-architecture/02-core-flow-and-state.md)
4. [首次接手与改需求入口](./03-first-day-handoff.md)
5. [API 与权限矩阵](../04-reference/01-api-and-permissions.md)
6. [内部协作接口](../04-reference/02-internal-collaboration.md)
7. [配置来源与运行依赖](../03-development-and-operations/02-configuration-and-dependencies.md)

## 前端或测试

建议顺序：

1. [项目概览](./01-project-overview.md)
2. [10 分钟跑通本地环境](./04-ten-minute-run.md)
3. [API 与权限矩阵](../04-reference/01-api-and-permissions.md)
4. [端口、依赖与队列清单](../04-reference/03-ports-dependencies-and-queues.md)
5. [排障手册](../03-development-and-operations/04-troubleshooting.md)

重点关注：

- 哪些接口可以匿名访问
- 哪些接口必须登录或管理员权限
- 联调入口统一走 `http://127.0.0.1:8080`

## 运维 / 平台

建议顺序：

1. [项目概览](./01-project-overview.md)
2. [配置来源与运行依赖](../03-development-and-operations/02-configuration-and-dependencies.md)
3. [发布与部署基线](../03-development-and-operations/03-release-and-deployment.md)
4. [端口、依赖与队列清单](../04-reference/03-ports-dependencies-and-queues.md)
5. [脚本与入口文件说明](../04-reference/04-scripts-and-entrypoints.md)

## 想追溯历史设计的人

建议顺序：

1. [历史演进背景](../05-appendices/01-historical-evolution.md)
2. [事件与派生视图](../02-architecture/03-events-and-derived-views.md)
3. [架构约束](../02-architecture/04-architecture-constraints.md)

## 想直接改需求的人

不要从 README 往下顺着全读，直接看：

1. [首次接手与改需求入口](./03-first-day-handoff.md)
2. [模块边界](../02-architecture/01-module-boundaries.md)
3. [内部协作接口](../04-reference/02-internal-collaboration.md)
4. [关键文件深度导读](../05-appendices/02-file-level-deep-dive.md)
