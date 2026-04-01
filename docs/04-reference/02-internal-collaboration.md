# 内部协作接口

适合谁看：需要看跨服务 HTTP 调用、内部接口用途和契约边界的人。  
读完能解决什么问题：知道哪些内部接口存在、为什么存在，以及该把跨服务调用放在哪一层。

## 先看结论

- 内部接口不是对外 API 的复制品，而是服务间协作契约
- 网关不暴露 `/internal/**`
- Feign client 应放在 `*-contract` 模块，而不是实现模块

## 当前内部接口

### `auth-service`

`/internal/users`

- `POST /internal/users/batch`

用途：

- 给其他服务批量查询用户摘要信息

### `content-service`

`/internal/articles`

- `GET /internal/articles/{id}/review-snapshot`
- `POST /internal/articles/{id}/apply-review-result`
- `POST /internal/articles/profile-page`

用途：

- 审核服务获取审核所需文章快照
- 内容服务应用审核结果
- 聚合用户主页文章数据

### `review-service`

`/internal/reviews`

- `GET /internal/reviews/articles/{id}/latest`
- `POST /internal/reviews/tasks/upsert`
- `POST /internal/reviews/tasks/remove`

用途：

- 获取文章最近审核信息
- 维护审核任务投影

## 为什么内部接口存在

它们主要解决三个问题：

- 服务之间交换标准事实，而不是互查数据库
- 把跨服务协作固定成稳定契约
- 减少派生视图服务对真源实现细节的耦合

## 使用内部接口时的原则

- 先判断是否已有契约模块可以复用
- 新增 Feign client 时放进对应 `*-contract`
- 不要让网关直接透出内部路径
- 不要把内部接口做成“对外接口的另一套重复包装”

## 什么时候优先用事件，不优先用内部 HTTP

- 目标是异步派生，而不是同步要一个事实结果
- 允许最终一致性
- 需要可重试或可补偿

例如通知和搜索更新就更适合走事件，而不是同步 RPC。
