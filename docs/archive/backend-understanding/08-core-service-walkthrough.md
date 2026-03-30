# 08 Core Service 逐文件讲解

## 为什么读这篇

这篇是当前仓库的业务核心。Controller 只是入口，真正决定“文章怎样流转、审核怎样落地、搜索怎样投影、上传怎样存、通知怎样生成”的地方都在这里。

## 本篇覆盖哪些文件

- `auth-service`：`AuthServiceImpl`、`UserServiceImpl`
- `content-service`：`ArticleServiceImpl`、`HomeServiceImpl`、`CategoryServiceImpl`
- `review-service`：`ReviewServiceImpl`、`ReviewEventServiceImpl`、`ReviewTaskServiceImpl`
- `search-service`：`SearchServiceImpl`、`SearchEventServiceImpl`、`SearchIndexSyncServiceImpl`
- `file-service`：`UploadServiceImpl`
- `notification-service`：`NotificationEventServiceImpl`

## `AuthServiceImpl`

文件位置：

- [../../auth-service/src/main/java/com/platform/service/impl/AuthServiceImpl.java](../../auth-service/src/main/java/com/platform/service/impl/AuthServiceImpl.java)

文件职责：

- 承接注册、登录、找回密码和重置密码的核心业务

关键行为：

- 校验账号注册约束
- 生成 JWT
- 处理密码重置令牌和邮件链路

依赖关系：

- 依赖用户 Mapper、密码编码器、Redis、邮件能力和 JWT 工具

修改风险：

- 改错会同时影响注册、登录态、密码找回

常见改动入口：

- 调整登录返回
- 调整密码策略
- 调整找回密码 token 行为

## `UserServiceImpl`

文件位置：

- [../../auth-service/src/main/java/com/platform/service/impl/UserServiceImpl.java](../../auth-service/src/main/java/com/platform/service/impl/UserServiceImpl.java)

文件职责：

- 处理用户资料查询、更新和内部用户摘要读取

关键行为：

- 读取当前用户资料
- 更新头像、昵称等资料
- 组装给其他服务使用的用户摘要

依赖关系：

- 依赖用户表 Mapper
- 被 `UserController` 和 `InternalUserController` 调用

修改风险：

- 用户摘要一旦变化，会联动搜索作者信息和其他引用方

常见改动入口：

- 新增公开资料字段
- 调整内部摘要字段

## `HomeServiceImpl`

文件位置：

- [../../content-service/src/main/java/com/platform/service/impl/HomeServiceImpl.java](../../content-service/src/main/java/com/platform/service/impl/HomeServiceImpl.java)

文件职责：

- 提供首页文章流的查询与组装

关键行为：

- 查询首页展示文章
- 组装首页卡片需要的作者、分类、摘要信息

依赖关系：

- 依赖内容域 Mapper
- 可能通过内部用户接口补作者信息

修改风险：

- 首页性能和分页行为通常受这里影响最大

常见改动入口：

- 首页排序调整
- 首页卡片字段调整

## `CategoryServiceImpl`

文件位置：

- [../../content-service/src/main/java/com/platform/service/impl/CategoryServiceImpl.java](../../content-service/src/main/java/com/platform/service/impl/CategoryServiceImpl.java)

文件职责：

- 提供分类查询和分类下内容查询

关键行为：

- 分类列表查询
- 分类文章聚合展示

依赖关系：

- 依赖分类与文章相关 Mapper

修改风险：

- 分类查询条件变化会影响首页、分类页和文章归属展示

常见改动入口：

- 分类排序和展示字段调整

## `ArticleServiceImpl`

文件位置：

- [../../content-service/src/main/java/com/platform/service/impl/ArticleServiceImpl.java](../../content-service/src/main/java/com/platform/service/impl/ArticleServiceImpl.java)

文件职责：

- 这是内容域最关键的业务服务，负责文章创建、更新、详情、提审和状态相关核心行为

关键行为：

- 创建和编辑文章
- 保存草稿后的正式落库逻辑
- 提交审核前的状态校验
- 维护文章状态真源
- 生成内容域需要发出的事件

依赖关系：

- 依赖 `ArticleMapper`
- 依赖草稿服务、内部用户信息、事件发件箱或 MQ 支持

修改风险：

- 这里是最容易破坏全链路一致性的文件
- 一旦文章状态推进逻辑改错，审核、通知、搜索都会连锁异常

常见改动入口：

- 新增文章字段
- 调整提审规则
- 调整状态迁移规则

## `ReviewServiceImpl`

文件位置：

- [../../review-service/src/main/java/com/platform/service/impl/ReviewServiceImpl.java](../../review-service/src/main/java/com/platform/service/impl/ReviewServiceImpl.java)

文件职责：

- 承接管理员执行审核动作的核心业务

关键行为：

- 校验审核动作是否合法
- 记录审核日志
- 产出审核决定结果

依赖关系：

- 依赖 `ReviewLogMapper`、`ReviewTaskMapper`
- 依赖 `ReviewEventServiceImpl` 发出后续结果

修改风险：

- 如果直接在这里越界写内容真源，就会破坏服务边界

常见改动入口：

- 新增审核动作
- 调整审核备注、原因或决策规则

## `ReviewEventServiceImpl`

文件位置：

- [../../review-service/src/main/java/com/platform/service/impl/ReviewEventServiceImpl.java](../../review-service/src/main/java/com/platform/service/impl/ReviewEventServiceImpl.java)

文件职责：

- 把审核决定转换为下游可消费的事件

关键行为：

- 生成审核决定事件
- 交给发件箱或 MQ 发布链路

依赖关系：

- 依赖 `common` 中的事件模型和事件常量
- 与 `ReviewOutboxPublisher` 配合

修改风险：

- 事件字段缺失或事件类型写错，会导致内容域无法正确消费审核结果

常见改动入口：

- 调整审核决定事件结构

## `ReviewTaskServiceImpl`

文件位置：

- [../../review-service/src/main/java/com/platform/service/impl/ReviewTaskServiceImpl.java](../../review-service/src/main/java/com/platform/service/impl/ReviewTaskServiceImpl.java)

文件职责：

- 管理审核任务投影和审核任务列表查询

关键行为：

- 根据提审事件创建或更新待审任务
- 提供审核列表查询能力

依赖关系：

- 依赖 `ReviewTaskMapper`
- 与 `ReviewEventListener` 这类 MQ 消费入口协作

修改风险：

- 它维护的是投影视图，不是真源；在这里写过多领域规则会让边界混乱

常见改动入口：

- 调整审核列表查询条件
- 调整任务投影字段

## `SearchServiceImpl`

文件位置：

- [../../search-service/src/main/java/com/platform/service/impl/SearchServiceImpl.java](../../search-service/src/main/java/com/platform/service/impl/SearchServiceImpl.java)

文件职责：

- 承接公开搜索请求并组装搜索结果

关键行为：

- 对 `title` 和 `summary` 做关键词搜索
- 归一化 `page` 与 `pageSize`
- 使用“相关性优先 + `publishedAt` 倒序兜底”的排序
- 尽力补全作者昵称和头像

依赖关系：

- 依赖 `ArticleSearchRepository`
- 依赖内部用户摘要接口做作者信息补全

修改风险：

- 改查询结构会直接影响前端搜索效果和 smoke 断言
- 作者补全如果从“尽力而为”改成强依赖，会增加搜索失败面

常见改动入口：

- 调整搜索字段
- 调整分页规则
- 调整排序策略

## `SearchEventServiceImpl`

文件位置：

- [../../search-service/src/main/java/com/platform/service/impl/SearchEventServiceImpl.java](../../search-service/src/main/java/com/platform/service/impl/SearchEventServiceImpl.java)

文件职责：

- 消费文章状态变化事件并维护搜索索引的增量同步

关键行为：

- `APPROVED` 时写入或刷新索引
- 非 `APPROVED` 时删除索引

依赖关系：

- 依赖 `ArticleSearchRepository`
- 依赖搜索文档模型和内容索引读取能力

修改风险：

- 这里改错最容易出现“文章首页可见但搜索不到”或“搜索还能搜到已下线文章”

常见改动入口：

- 调整索引写入字段
- 调整索引删除条件

## `SearchIndexSyncServiceImpl`

文件位置：

- [../../search-service/src/main/java/com/platform/service/impl/SearchIndexSyncServiceImpl.java](../../search-service/src/main/java/com/platform/service/impl/SearchIndexSyncServiceImpl.java)

文件职责：

- 在服务启动时执行已发布文章索引回填与对齐

关键行为：

- 扫描当前数据库中的已发布文章
- 补写缺失索引
- 清理不该保留的旧索引文档

依赖关系：

- 依赖 `SearchIndexMapper`
- 依赖 `ArticleSearchRepository`

修改风险：

- 这是修复历史索引漂移的重要兜底，如果删掉或改坏，历史数据与搜索结果很难自动重新对齐

常见改动入口：

- 调整启动回填范围
- 增加人工重建索引能力时

## `UploadServiceImpl`

文件位置：

- [../../file-service/src/main/java/com/platform/service/impl/UploadServiceImpl.java](../../file-service/src/main/java/com/platform/service/impl/UploadServiceImpl.java)

文件职责：

- 承接上传校验、落盘和访问 URL 生成

关键行为：

- 校验 MIME 类型和大小
- 生成文件名
- 将文件写入配置目录
- 返回外部访问路径

依赖关系：

- 依赖 `StorageConfig`
- 受 `WebMvcConfig` 中的访问映射配合

修改风险：

- 这里只改存储路径不改访问映射，上传链路就会变成“写成功但访问不到”

常见改动入口：

- 放宽文件类型
- 改成本地以外的存储方案

## `NotificationEventServiceImpl`

文件位置：

- [../../notification-service/src/main/java/com/platform/service/impl/NotificationEventServiceImpl.java](../../notification-service/src/main/java/com/platform/service/impl/NotificationEventServiceImpl.java)

文件职责：

- 承接文章状态变化后的通知生成与投递记录落库

关键行为：

- 消费文章状态变化后的业务语义
- 组装通知主记录
- 组装通知投递记录

依赖关系：

- 依赖 `NotificationMapper`
- 依赖 `NotificationDeliveryMapper`
- 依赖 `common` 中的文章状态变更事件模型

修改风险：

- 它是派生服务，不能倒过来承担内容真源判断
- 如果通知去重或投递记录逻辑改错，最容易出现重复通知或通知漏记

常见改动入口：

- 调整通知文案字段
- 新增通知类型
- 调整投递记录策略

## Service 层应该承担什么

- 领域状态推进
- 事务与持久化协调
- 外部依赖编排
- 事件生成与派生数据同步入口

它不应该变成：

- 纯粹的 Controller 搬运层
- 无边界的跨服务脚本
- 到处直接拼装返回对象而没有稳定领域语义
