# 首次接手与改需求入口

适合谁看：准备接手仓库、排查需求变更落点、评估改动影响的后端同学。  
读完能解决什么问题：明确改动应该从哪个服务和哪类文档入手，而不是在整个仓库里盲搜。

## 先抓住三条真源原则

- 用户与认证真源是 `auth-service`
- 文章主状态真源是 `content-service`
- 审核动作真源是 `review-service`

搜索和通知都不是事实真源，它们是文章状态变化后的派生结果。

## 常见需求从哪里进

### 改注册、登录、找回密码、用户资料

先看：

- [API 与权限矩阵](../04-reference/01-api-and-permissions.md)
- [内部协作接口](../04-reference/02-internal-collaboration.md)
- `auth-service`

### 改文章创建、草稿、提审、详情、首页、分类

先看：

- [模块边界](../02-architecture/01-module-boundaries.md)
- [核心链路与状态流转](../02-architecture/02-core-flow-and-state.md)
- `content-service`

### 改审核规则、审核待办、审核日志

先看：

- [核心链路与状态流转](../02-architecture/02-core-flow-and-state.md)
- [内部协作接口](../04-reference/02-internal-collaboration.md)
- `review-service`

### 改搜索结果、搜索索引同步、搜索可见性

先看：

- [事件与派生视图](../02-architecture/03-events-and-derived-views.md)
- [端口、依赖与队列清单](../04-reference/03-ports-dependencies-and-queues.md)
- `search-service`

### 改通知生成、投递记录、通知渠道

先看：

- [事件与派生视图](../02-architecture/03-events-and-derived-views.md)
- `notification-service`

### 改鉴权、网关路由、公开接口规则

先看：

- [API 与权限矩阵](../04-reference/01-api-and-permissions.md)
- `gateway-service`
- `platform-kernel` 的请求头与事件常量

## 改需求前的最小检查单

1. 先确认你改的是“真源”还是“派生视图”
2. 确认这条能力是公网 API、内部接口，还是事件消费逻辑
3. 确认是否会影响网关公开路由、权限边界、内部头协议
4. 确认是否会影响队列、事件、搜索投影或通知投影
5. 确认是否需要同步调整冒烟脚本或部署文档

## 什么时候必须看架构测试

出现下面几类改动时，先看 [架构约束](../02-architecture/04-architecture-constraints.md)：

- 新增 Feign client
- 提取共享基础设施
- 调整服务启动入口
- 调整事件基础设施目录归属
- 重新引入类似 `common` 的共享大模块

## 如果你只想用 5 分钟找到落点

- 找公网接口：看 [API 与权限矩阵](../04-reference/01-api-and-permissions.md)
- 找内部接口：看 [内部协作接口](../04-reference/02-internal-collaboration.md)
- 找主链路状态：看 [核心链路与状态流转](../02-architecture/02-core-flow-and-state.md)
- 找脚本和运行入口：看 [脚本与入口文件说明](../04-reference/04-scripts-and-entrypoints.md)
- 找关键文件：看 [关键文件深度导读](../05-appendices/02-file-level-deep-dive.md)
