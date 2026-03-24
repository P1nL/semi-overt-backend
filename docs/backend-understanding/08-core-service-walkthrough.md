# 核心 Service 逐文件讲解

这一篇是整个项目最重要的一篇。真正的业务规则几乎都写在 `service/impl` 里。

## 先看 Service 分层

主业务 Service：

- `AuthServiceImpl`
- `ArticleServiceImpl`
- `DraftServiceImpl`
- `ReviewServiceImpl`
- `UserServiceImpl`
- `HomeServiceImpl`
- `CategoryServiceImpl`
- `SearchServiceImpl`
- `UploadServiceImpl`

支撑型 Service：

- `UserDetailsServiceImpl`

下面优先讲主线最强的几个。

## 1. `AuthServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/AuthServiceImpl.java`

职责：

- 注册
- 登录
- 登出
- 找回密码邮件
- 重置密码

依赖：

- `UserMapper`
- `PasswordEncoder`
- `JwtHelper`
- `StringRedisTemplate`
- `JavaMailSender`

### 你应该抓住的 5 个关键点

1. 注册时直接创建 `User` 并发 token
2. 登录支持用户名或邮箱
3. 登出不是删 token，而是把 token 放进 Redis 黑名单
4. 忘记密码分两步：发邮件、重置密码
5. Redis key 命名已经形成规范

### Redis Key 规范

- `jwt:blacklist:{token}`
- `pwd:reset:{uuid}`
- `pwd:reset:lock:{email}`

### 读这个文件时最值得学的设计

- “邮箱不存在也返回成功”防止邮箱枚举攻击
- 重置邮件发送失败时会把 Redis 里的临时状态回滚
- 注册后的默认昵称就是用户名，减少前端首屏空状态

### 修改风险点

- 改 token 结构会同时影响 `JwtHelper`、前端存储、续签逻辑
- 改重置密码链接路径会影响前端路由
- 改用户名规则时，记得保留系统保留词校验

## 2. `ArticleServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/ArticleServiceImpl.java`

职责：

- 创建空文章
- 文章详情读取
- 提交审核
- 取消审核
- 删除文章
- 管理员删除文章

依赖：

- `ArticleMapper`
- `ReviewLogMapper`
- `UserMapper`
- `StringRedisTemplate`

### 这个文件真正控制的业务规则

- 谁能读一篇文章
- 谁能提交审核
- 哪些状态能取消审核
- 哪些状态能删除
- 是否命中提交审核冷却时间

### 最关键的方法

#### `getArticleDetail`

它不是简单查表，而是做了 4 件事：

1. 查文章
2. 校验当前用户是否有权限看
3. 如果状态是 `RETURNED / REJECTED`，补最近审核原因
4. 如果文章仍在草稿态或退回态，优先读 Redis 里的最新正文

这说明“文章详情”不是单纯数据库快照，而是“数据库 + Redis + 权限判断”的组合结果。

#### `submitForReview`

它是文章状态流最关键的方法。

它会依次做：

1. 查文章并校验作者身份
2. 限制只能从 `DRAFT / RETURNED` 提交
3. 读取最新正文，优先 Redis
4. 检查正文最小长度
5. 检查 30 分钟冷却时间
6. 把 Redis 草稿正文刷回文章实体
7. 状态改成 `PENDING`
8. 更新 `submitCount` 和 `lastSubmittedAt`

#### `cancelReview`

它会：

- 把状态从 `PENDING` 改回 `DRAFT`
- 写一条 `ReviewLog`，动作是 `CANCEL`

这很重要，因为“取消审核”虽然是作者动作，但依然被放进统一审核历史里。

### 这个文件最容易踩的坑

- 读取权限判断不是只看登录，还和文章状态绑定。
- 文章详情优先读 Redis 的分支只对 `DRAFT / RETURNED` 生效。
- 删除文章走的是逻辑删除，别误以为表里数据真的消失。

## 3. `DraftServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/DraftServiceImpl.java`

职责：

- 自动保存草稿
- 草稿箱列表
- Redis 草稿刷盘

依赖：

- `ArticleMapper`
- `ReviewLogMapper`
- `StringRedisTemplate`

### 这个文件的设计核心

正文高频更新写 Redis，元数据写 MySQL。

对应策略：

- `content` 放 Redis
- `title / summary / coverUrl / coverColor / wordCount / readMinutes / durationCategory` 放 MySQL

这样做的原因：

- 编辑器会高频自动保存
- MySQL 不适合每几秒刷一次大段正文

### `saveDraft` 的逻辑重点

- 只允许 `DRAFT / RETURNED` 状态编辑
- 作者才能保存
- `content` 进 Redis，并重置 TTL
- 同时重新计算 `wordCount / readMinutes / durationCategory`
- 只更新非 null 字段，支持前端“增量保存”

### `getDraftList`

返回的是：

- `DRAFT`
- `RETURNED`

而不是所有未发布文章。因为 `PENDING` 已经锁定，不再属于“可编辑草稿”。

### `flushAllDrafts`

它扫描所有 `draft:*` key，把正文刷回 MySQL `articles.content`。

这里是这个项目里 Redis 和 MySQL 一致性的关键补偿点。

### 风险点

- `DRAFT_TTL_DAYS` 写死成常量 7，没真正使用配置项。
- 字数计算逻辑和 [ArticleUtils.java](./10-supporting-files-walkthrough.md) 有重复实现，后面维护时需要注意一致性。

## 4. `ReviewServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/ReviewServiceImpl.java`

职责：

- 获取待审核列表
- 审核动作处理
- 审核日志查询

依赖：

- `ArticleMapper`
- `ReviewLogMapper`
- `UserMapper`

### `getPendingList`

特点：

- 只查 `PENDING`
- 排除当前管理员自己提交的文章
- 返回审核列表 DTO，而不是直接返回 `Article`

### `doReview`

这是管理员审核的主入口。它做的事很明确：

1. 把 `req.action` 解析成枚举
2. 禁止 `CANCEL`
3. `RETURN / REJECT` 必须填写原因
4. 校验文章还处于 `PENDING`
5. 管理员不能审核自己的文章
6. 按动作改状态
7. `APPROVE` 时写 `publishedAt`
8. 写 `review_logs`

### `getReviewLogs`

权限规则：

- 管理员可以看任意文章日志
- 作者可以看自己的文章日志
- 其他用户不行

注意：

- 日志按时间升序返回，是为了给前端做时间轴

## 5. `UserServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/UserServiceImpl.java`

职责：

- 当前用户信息
- 修改资料
- 用户主页

依赖：

- `UserMapper`
- `ArticleMapper`
- `ReviewLogMapper`

### 这个文件最值得注意的是“同一个主页接口的双视图”

变量：

- `isSelf`
- `isAdmin`
- `canViewAll`

决定了两件事：

- 统计数据是不是全量
- 文章列表是不是只看 `APPROVED`

### `getUserProfile`

这一个方法同时完成：

1. 查目标用户
2. 判断当前访问者身份
3. 统计文章状态数量
4. 解析 tab
5. 分页查文章
6. 如果当前看的是 `REJECTED`，批量补拒绝原因
7. 组装用户主页响应

它是一个典型的“页面聚合型 Service”。

### 风险点

- `resolveTabStatus` 对非法 tab 默认返回 `null`，相当于退回 `all` 逻辑，不会报错。
- 统计和列表查询是分开的，后续如果要加复杂筛选，得保证两者口径一致。

## 6. `HomeServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/HomeServiceImpl.java`

职责：

- 首页 Hero 和 3 个分类区块的数据聚合

依赖：

- `ArticleMapper`
- `UserMapper`
- `StringRedisTemplate`
- `ObjectMapper`

### 核心设计

首页 Hero 不是每次随机，而是“每天随机一次，然后缓存”。

Redis key：

- `home:hero:{yyyy-MM-dd}`

流程：

1. 先查当天缓存
2. 没缓存就随机取 5 篇 `APPROVED`
3. 把文章 ID 缓存到当天结束
4. 后续同一天的访问都看到同一组 Hero

这让首页“既有随机感，又不会每次刷新都变”。

## 7. `CategoryServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/CategoryServiceImpl.java`

职责：

- 分类页列表分页

特点：

- 先把字符串分类参数转成 `DurationCategory`
- 非法分类直接抛 400
- 文章和作者信息分两步查，再拼卡片响应

## 8. `SearchServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/SearchServiceImpl.java`

职责：

- 搜索接口的占位实现

当前状态：

- 固定返回空列表
- API 已经稳定
- 未来可以直接替换为 LIKE 查询或 Elasticsearch

这说明项目在“接口先行、实现后补”。

## 9. `UploadServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/UploadServiceImpl.java`

职责：

- 图片上传
- 格式校验
- 路径生成
- 封面图主色提取

### 主要步骤

1. 校验文件存在
2. 校验 `bizType`
3. 校验文件大小
4. 校验 MIME 类型
5. 校验扩展名
6. 用 `ImageIO` 读图
7. 必要时提取主色
8. 按日期目录写入磁盘
9. 返回 `/static/uploads/...` 访问地址

### 风险点

- 文件大小这里写死 5MB，和配置不完全一致。
- 只允许图片上传，没有抽象成通用文件上传。

## 10. `UserDetailsServiceImpl.java`

文件：`src/main/java/com/platform/service/impl/UserDetailsServiceImpl.java`

职责：

- 提供 Spring Security 兼容的 `UserDetailsService`

注意：

- 当前登录主链路实际上不靠它。
- 认证主链路是 `AuthServiceImpl` 自己查库 + BCrypt 校验。
- 这个类更像“满足框架要求 + 为未来扩展预留”。

这意味着如果你调试当前登录问题，先看 `AuthServiceImpl`，不是先看这个类。
