# 03 数据与事件模型

## 为什么读这份文档

这份文档的目标是帮你区分“主数据、缓存、投影、索引、事件”这些概念。只有先把数据语义想清楚，后面改功能时才不会把 Redis、MySQL、RabbitMQ、Elasticsearch 全部当成同一种存储。

## 五类数据要先分清

### 真源数据

真源数据是系统最终裁定某个业务事实的地方。

当前典型真源：

- 用户信息：`auth-service` MySQL
- 文章主状态与元数据：`content-service` MySQL
- 审核日志与审核任务：`review-service` MySQL

### 缓存数据

缓存数据是为了性能和交互体验服务的临时数据，不是业务最终真相。

当前典型缓存：

- 草稿正文缓存
- JWT 黑名单
- 找回密码 token
- 首页 Hero 缓存

### 派生数据

派生数据是由业务主事实变化后推导出来的结果。

当前典型派生数据：

- 通知主记录
- 通知投递记录
- 审核任务投影

### 索引数据

索引数据是为了检索体验构建的专用投影。

当前典型索引数据：

- `search-service` 的 Elasticsearch `articles` 索引

### 事件数据

事件数据是服务之间传播业务事实变化的媒介。

当前典型事件：

- `ArticleSubmittedEvent`
- `ReviewDecidedEvent`
- `ArticleStatusChangedEvent`

## 中间件分别承担什么

### MySQL

承担主业务数据与派生持久化结果。

### Redis

承担鉴权短期状态、草稿缓存和部分聚合缓存。

### RabbitMQ

承担审核、通知、搜索链路中的异步事件传播。

### Elasticsearch

承担公开搜索投影，只服务于检索体验，不承担文章真源职责。

### Nacos

承担服务发现和配置中心职责。

## 事件链路如何理解

事件常量集中定义在 [EventConstants.java](../../common/src/main/java/com/platform/common/constant/EventConstants.java)。

### `ArticleSubmittedEvent`

表示文章已经提交审核，审核域应建立待审任务投影。

### `ReviewDecidedEvent`

表示审核域已经做出决定，内容域应把这个决定落成主状态变化。

### `ArticleStatusChangedEvent`

表示内容域主状态已经改变，通知与搜索应据此更新派生视图。

## 改数据相关需求时要先判断什么

- 改主表字段：先判断这是谁的真源字段
- 改缓存逻辑：先判断缓存 miss 后能否靠真源恢复
- 改事件字段：先判断会不会影响多个消费者兼容性
- 改搜索字段：先判断这是投影字段还是主数据字段
