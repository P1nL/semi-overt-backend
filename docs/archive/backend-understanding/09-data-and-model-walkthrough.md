# 数据模型与持久化逐文件讲解

这一篇解决两个问题：

- 数据库里到底存了什么。
- 哪些查询直接走 `BaseMapper`，哪些走 XML。

## 1. `init.sql`

文件：`src/main/resources/init.sql`

这是数据库初始化脚本，也是理解实体最直接的入口。

### 三张核心表

- `users`
- `articles`
- `review_logs`

### `users`

核心字段：

- `username`
- `email`
- `password`
- `role`
- `avatar_url`
- `cover_url`
- `signature`

你从表结构就能看出：

- 用户体系很轻量，没有权限组、没有部门、没有软删。
- `username` 和 `email` 都唯一。

### `articles`

核心字段：

- `author_id`
- `title`
- `content`
- `summary`
- `cover_url`
- `cover_color`
- `word_count`
- `read_minutes`
- `duration_category`
- `status`
- `submit_count`
- `last_submitted_at`
- `published_at`
- `deleted`

这个表本质上同时承载了：

- 草稿元数据
- 正式内容
- 审核状态
- 展示统计信息

### `review_logs`

核心字段：

- `article_id`
- `operator_id`
- `action`
- `from_status`
- `to_status`
- `reason`

它是文章状态变更的历史表，不只是管理员动作，也包含作者取消审核。

## 2. `User.java`

文件：`src/main/java/com/platform/entity/User.java`

作用：

- MyBatis Plus 用户实体
- 对应 `users` 表

你看这个类时要关注：

- `@TableName("users")`
- `@TableId(type = IdType.AUTO)`
- `createdAt / updatedAt` 自动填充

不要把它和 `UserInfoResp / UserProfileResp` 混在一起。`User` 是数据库视角，DTO 是接口视角。

## 3. `Article.java`

文件：`src/main/java/com/platform/entity/Article.java`

作用：

- 文章主实体
- 对应 `articles` 表

这个类最重要的字段是：

- `authorId`
- `status`
- `submitCount`
- `lastSubmittedAt`
- `publishedAt`
- `deleted`

理解这个实体时要特别记住：

- 草稿正文理论上也会落到 `content`，但编辑高频阶段最新版本可能只在 Redis。
- `deleted` 是逻辑删除字段，不是物理删除。

## 4. `ReviewLog.java`

文件：`src/main/java/com/platform/entity/ReviewLog.java`

作用：

- 审核历史实体
- 对应 `review_logs`

这个类和状态机关系最紧密。任何审核动作、取消动作，最后都应该映射成一条日志。

## 5. `ArticleMapper.java`

文件：`src/main/java/com/platform/mapper/ArticleMapper.java`

它继承了 `BaseMapper<Article>`，因此天然拥有：

- `selectById`
- `insert`
- `updateById`
- `deleteById`
- `selectPage`

除了通用 CRUD，这个接口额外声明了复杂查询：

- `selectHeroPrimary`
- `selectHeroSecondary`
- `selectRandomApproved`
- `selectApprovedByCategory`
- `selectPageByCategory`
- `searchByKeyword`

读法上要建立一个区分：

- 单表基础操作，优先找 `BaseMapper`
- 带筛选策略或页面聚合意图的查询，优先找 XML

## 6. `ArticleMapper.xml`

文件：`src/main/resources/mapper/ArticleMapper.xml`

### 它为什么存在

因为有些查询用 LambdaWrapper 写出来不直观，或者团队更想把 SQL 显式写出来。

### 主要查询

#### `selectRandomApproved`

- 首页 Hero 随机选文章
- 使用 `ORDER BY RAND() LIMIT #{limit}`

这对小数据量项目没问题，但将来数据量大时会是优化点。

#### `selectApprovedByCategory`

- 首页每个时长分区取固定数量文章

#### `selectPageByCategory`

- 分类页分页数据源

#### `searchByKeyword`

- 当前是基于标题、摘要的 LIKE 查询预留
- 但主流程里实际还没启用，因为 `SearchServiceImpl` 目前返回空结果

### 一个重要认识

这个 XML 只操作 `Article` 主表，不做复杂多表对象映射。作者信息大多在 Service 层二次查 `UserMapper` 再组装 DTO。

这说明当前项目的数据访问策略偏简单直接，不是重 XML 关联映射风格。

## 7. `UserMapper.java` 和 `ReviewLogMapper.java`

文件：

- `src/main/java/com/platform/mapper/UserMapper.java`
- `src/main/java/com/platform/mapper/ReviewLogMapper.java`

这两个 Mapper 都只继承了 `BaseMapper`，没有额外自定义方法。

这带来的结论是：

- 用户查询目前都比较简单
- 审核日志查询也基本用 Wrapper 就够了

如果以后这两个 Mapper 变复杂，通常说明业务正在增长。

## 8. 数据层的整体风格

这个项目的数据层风格可以概括成：

- 基础 CRUD 用 `BaseMapper`
- 复杂查询少量下沉到 XML
- 聚合 DTO 组装在 Service 层完成
- Redis 只做缓存和短期状态，不当成主存储

## 9. 最常见的改动入口

改数据库字段：

1. `init.sql`
2. 对应 `entity`
3. 对应 DTO
4. `ServiceImpl`
5. 如果有自定义查询，再改 XML

改查询逻辑：

- 先看能不能用 `BaseMapper + Wrapper`
- 不够清晰再加到 `ArticleMapper.xml`

改状态相关逻辑：

- 不要只改表或实体，一定一起看 `ArticleServiceImpl / ReviewServiceImpl / ReviewLog`
