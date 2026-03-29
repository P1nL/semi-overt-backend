# 支撑模块与公共类型逐文件讲解

这一篇讲那些“不一定直接暴露为主接口，但不懂它们就容易误判系统行为”的文件。

## 1. `WebMvcConfig.java`

文件：`src/main/java/com/platform/config/WebMvcConfig.java`

职责：

- 配置跨域
- 配置本地静态资源映射

### 跨域配置

当前放行的前端开发地址：

- `http://localhost:5173`
- `http://localhost:3000`
- `http://127.0.0.1:5173`

并且显式暴露了响应头：

- `New-Token`
- `Authorization`

这就是前端为什么能在浏览器里读取续签 token。

### 静态资源映射

把：

- `/static/uploads/**`

映射到：

- `storage.upload-path` 指向的本地目录

这条映射是“文件明明上传成功了，但浏览器访问 404”时最先检查的地方。

## 2. `RedisConfig.java`

文件：`src/main/java/com/platform/config/RedisConfig.java`

职责：

- 提供 `StringRedisTemplate`
- 提供支持 `LocalDateTime` 的 `ObjectMapper`

### 设计风格

项目明确把 Redis 当成“字符串键值存储”在用，没有上复杂对象模板。这使得：

- key 可读性高
- 调试容易
- 代价是复杂结构需要手动序列化

首页 Hero 的文章 ID 列表就是这样手工 JSON 化的。

## 3. `StorageConfig.java`

文件：`src/main/java/com/platform/config/StorageConfig.java`

职责：

- 把 `application.yml` 中的 `storage.*` 绑定成配置对象

当前特点：

- 是典型的配置载体类
- 当前上传主流程主要用的是 `@Value`
- 这个类更像为后续抽象统一文件存储服务做准备

## 4. `MybatisPlusConfig.java`

文件：`src/main/java/com/platform/config/MybatisPlusConfig.java`

职责：

- 分页插件
- 防全表更新/删除
- 自动填充时间字段

### 关键点

- `PaginationInnerInterceptor`
  - 支持分页查询。
- `BlockAttackInnerInterceptor`
  - 防止无条件 `UPDATE / DELETE`。
- `AutoFillHandler`
  - 自动填 `createdAt / updatedAt`。

这解释了为什么很多实体没有手写更新时间逻辑，但字段仍然会变。

## 5. `DraftFlushTask.java`

文件：`src/main/java/com/platform/task/DraftFlushTask.java`

职责：

- 定时调用 `draftService.flushAllDrafts()`

调度方式：

- `@Scheduled(fixedDelay = 5 * 60 * 1000L)`

注意：

- 当前间隔写死在代码里，不是从配置读取。
- 顶层 try-catch 是为了避免一次异常让整个定时任务停摆。

这是草稿最终一致性的最后一道保险。

## 6. `ArticleUtils.java`

文件：`src/main/java/com/platform/util/ArticleUtils.java`

职责：

- 统计字数
- 计算阅读时长
- 计算阅读时长分类
- 生成纯文本预览

### 它在项目里的角色

- 首页
- 分类页
- 用户主页文章卡片

都依赖它生成预览文本。

### 维护时要注意

- `DraftServiceImpl` 里有一套相似但不完全相同的字数计算逻辑。
- 如果将来统一“字数口径”，这两个地方需要一起看。

## 7. DTO 目录应该怎么理解

目录：

- `src/main/java/com/platform/dto/req`
- `src/main/java/com/platform/dto/resp`

### `req DTO`

它们是“前端发给后端”的格式，例如：

- `LoginReq`
- `RegisterReq`
- `SaveDraftReq`
- `ReviewActionReq`
- `UpdateProfileReq`

特点：

- 经常带 `@NotBlank`、`@Email` 等校验
- 字段只保留接口真正需要的内容

### `resp DTO`

它们是“后端返回给前端”的格式，例如：

- `AuthResp`
- `HomeResp`
- `ArticleDetailResp`
- `UserProfileResp`
- `UploadResp`

特点：

- 面向页面结构，不一定等于数据库表结构
- 可能嵌套作者信息、分页信息、统计信息

最典型的例子是：

- `ArticleDetailResp` 里有嵌套 `author`
- `UserProfileResp` 里同时有 `profile / stats / list`

这都不是单表能直接表达的结构。

## 8. 枚举是业务词典

目录：

- `src/main/java/com/platform/enums`

### `ArticleStatus`

- `DRAFT`
- `PENDING`
- `APPROVED`
- `RETURNED`
- `REJECTED`

它定义了文章状态机。

### `ReviewAction`

- `APPROVE`
- `RETURN`
- `REJECT`
- `CANCEL`

它定义了审核动作词汇表。

### `DurationCategory`

- `QUICK`
- `SHORT`
- `DEEP`

它定义了阅读时长分类，不是用户手工选的栏目。

### `UserRole`

- `USER`
- `ADMIN`

权限模型非常简单，项目里没有更细粒度角色树。

### `BizType`

- `AVATAR`
- `COVER`
- `ARTICLE_IMAGE`

它决定上传接口的业务场景。

## 9. 这些支撑文件一起看时的判断方法

遇到问题时可以快速定位：

- 跨域问题：先看 `WebMvcConfig`
- 图片访问不到：先看 `WebMvcConfig + UploadServiceImpl + application.yml`
- 分页异常：先看 `MybatisPlusConfig`
- 草稿丢失或不落库：先看 `DraftServiceImpl + DraftFlushTask + Redis`
- DTO 看不懂：先分清 `req` 和 `resp`
- 状态名看不懂：先看 `enums`
