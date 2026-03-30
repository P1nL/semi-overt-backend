# 09 数据与模型逐文件讲解

## 为什么读这篇

这篇用来解决一个经常被混淆的问题：当前仓库里哪些数据是真源，哪些是派生投影，哪些只是内部传输契约。如果这个层次没理清，最常见的后果就是改了一个 DTO，却以为自己改的是业务真源；或者看见 ES 文档，就误以为那才是文章主数据。

## 本篇覆盖哪些文件

- 内容、审核、通知、搜索中的关键实体和索引文档
- `common` 中的内部 DTO 与事件模型
- 关键 Mapper、XML 与 Repository

## `Article`

文件位置：

- [../../content-service/src/main/java/com/platform/entity/Article.java](../../content-service/src/main/java/com/platform/entity/Article.java)

文件职责：

- 表示内容域中的文章真源实体

关键行为：

- 承载文章标题、摘要、正文、作者、分类、状态、发布时间等主数据

依赖关系：

- 由 `ArticleMapper` 持久化
- 被 `ArticleServiceImpl` 使用
- 会被内部接口、首页、详情、审核链路引用

修改风险：

- 文章字段一旦变化，首页、详情、审核、搜索、通知都可能联动

常见改动入口：

- 新增文章字段
- 调整状态字段语义

## `ArticleMapper`

文件位置：

- [../../content-service/src/main/java/com/platform/mapper/ArticleMapper.java](../../content-service/src/main/java/com/platform/mapper/ArticleMapper.java)
- [../../content-service/src/main/resources/mapper/ArticleMapper.xml](../../content-service/src/main/resources/mapper/ArticleMapper.xml)

文件职责：

- 承接文章实体与 MySQL 的查询、写入和复杂 SQL 映射

关键行为：

- 首页列表、分类列表、详情、文章管理侧查询通常都要经过这里

依赖关系：

- 被 `ArticleServiceImpl`、`HomeServiceImpl`、`CategoryServiceImpl` 调用

修改风险：

- SQL 改动最容易造成线上行为变化但编译仍然通过
- 这里还要注意区分当前模块内 XML 和根目录历史单体 `src` 下同名残留文件

常见改动入口：

- 改列表排序
- 改详情查询字段
- 新增文章后台查询条件

## `ReviewTask`

文件位置：

- [../../review-service/src/main/java/com/platform/entity/ReviewTask.java](../../review-service/src/main/java/com/platform/entity/ReviewTask.java)

文件职责：

- 表示审核域中的待审任务投影

关键行为：

- 保存“哪篇文章需要审核、当前审核任务是什么状态”这类信息

依赖关系：

- 由 `ReviewTaskMapper` 读写
- 被 `ReviewTaskServiceImpl` 和 `ReviewController` 使用

修改风险：

- 它是投影，不是真源；不要在这里塞入取代内容真源的字段和规则

常见改动入口：

- 调整审核列表展示字段

## `ReviewLog`

文件位置：

- [../../review-service/src/main/java/com/platform/entity/ReviewLog.java](../../review-service/src/main/java/com/platform/entity/ReviewLog.java)

文件职责：

- 保存审核动作日志

关键行为：

- 记录是谁在什么时间对哪篇文章做了什么审核决定以及备注

依赖关系：

- 由 `ReviewLogMapper` 持久化
- 被 `ReviewServiceImpl` 写入

修改风险：

- 审核日志是审计数据，字段改动要考虑历史兼容和排障价值

常见改动入口：

- 新增审核备注字段
- 调整日志展示需求

## `Notification`

文件位置：

- [../../notification-service/src/main/java/com/platform/entity/Notification.java](../../notification-service/src/main/java/com/platform/entity/Notification.java)

文件职责：

- 表示通知主记录

关键行为：

- 保存通知面向用户的主信息

依赖关系：

- 被 `NotificationMapper` 持久化
- 被 `NotificationEventServiceImpl` 生成

修改风险：

- 它是派生数据，不应反过来驱动文章状态

常见改动入口：

- 调整通知文案模板对应的字段

## `NotificationDelivery`

文件位置：

- [../../notification-service/src/main/java/com/platform/entity/NotificationDelivery.java](../../notification-service/src/main/java/com/platform/entity/NotificationDelivery.java)

文件职责：

- 表示通知投递记录

关键行为：

- 记录通知是否已投递、何时投递、投递结果如何

依赖关系：

- 由 `NotificationDeliveryMapper` 持久化
- 被 `NotificationEventServiceImpl` 写入

修改风险：

- 如果把投递结果和通知主记录混在一起，会让重试和审计变得不清晰

常见改动入口：

- 增加投递状态字段
- 增加失败原因字段

## `ArticleSearchDocument`

文件位置：

- [../../search-service/src/main/java/com/platform/document/ArticleSearchDocument.java](../../search-service/src/main/java/com/platform/document/ArticleSearchDocument.java)

文件职责：

- 表示 Elasticsearch 中的文章搜索文档

关键行为：

- 保存搜索场景需要的投影字段，例如标题、摘要、作者、发布时间等

依赖关系：

- 由 `ArticleSearchRepository` 读写
- 由 `SearchEventServiceImpl` 和 `SearchIndexSyncServiceImpl` 维护
- 被 `SearchServiceImpl` 查询

修改风险：

- 这是索引投影，不是真源；字段删改要同时考虑写入端和查询端

常见改动入口：

- 新增搜索结果展示字段
- 调整索引结构

## `ArticleSearchRepository`

文件位置：

- [../../search-service/src/main/java/com/platform/repository/ArticleSearchRepository.java](../../search-service/src/main/java/com/platform/repository/ArticleSearchRepository.java)

文件职责：

- 这是搜索服务操作 Elasticsearch 文档的 Repository 入口

关键行为：

- 写入、更新、删除、查询 `ArticleSearchDocument`

依赖关系：

- 被搜索查询和搜索索引同步服务使用

修改风险：

- 查询方法与文档字段不一致时，最容易出现“没有报错但就是查不出结果”

常见改动入口：

- ES 文档字段调整
- 查询方式调整

## `SearchIndexMapper`

文件位置：

- [../../search-service/src/main/java/com/platform/mapper/SearchIndexMapper.java](../../search-service/src/main/java/com/platform/mapper/SearchIndexMapper.java)

文件职责：

- 为搜索启动回填提供数据库侧已发布文章读取能力

关键行为：

- 查询当前数据库中的 `APPROVED` 文章，组装成可写入 ES 的数据源

依赖关系：

- 被 `SearchIndexSyncServiceImpl` 使用

修改风险：

- 启动回填依赖它的查询结果，如果这里漏字段或筛选条件错，索引修复就会失真

常见改动入口：

- 回填范围变化
- 索引字段来源变化

## 内部 DTO

文件位置：

- `common/src/main/java/com/platform/common/dto/internal/*.java`

文件职责：

- 这些 DTO 承担跨服务最小必要数据传输

关键行为：

- 用户摘要、文章摘要、审核摘要等内部调用载体通常放在这里

依赖关系：

- 被 Feign 或内部 HTTP 接口返回使用

修改风险：

- 它们不是数据库真源，也不是前端展示 DTO；但一旦变化，会同时影响多个调用方

常见改动入口：

- 给内部服务新增最小必要字段

## 事件模型

文件位置：

- `common/src/main/java/com/platform/common/event/*.java`
- [../../common/src/main/java/com/platform/common/constant/EventConstants.java](../../common/src/main/java/com/platform/common/constant/EventConstants.java)

文件职责：

- 定义跨服务异步链路使用的事件结构和事件常量

关键行为：

- 当前主事件包括文章提审、审核决定、文章状态变更
- 当前主消费队列包括审核投影、内容状态回写、通知生成和搜索索引同步

依赖关系：

- 被内容、审核、通知、搜索各服务共同使用

修改风险：

- 事件字段变化具有最高联动性
- 事件类型名、交换机、队列名一旦变化，需要检查所有生产和消费方

常见改动入口：

- 新增事件
- 增加事件字段
- 调整队列路由

## 这篇的核心判断标准

- `Article` 是真源数据
- `ReviewTask`、`Notification`、`NotificationDelivery`、`ArticleSearchDocument` 都是派生投影或派生记录
- 内部 DTO 是契约，不是真源
- 事件模型是跨服务协作协议，不是数据库表结构的替身
