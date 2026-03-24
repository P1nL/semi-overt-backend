# 04 改需求入口清单

这份清单的目标是：以后你接到需求时，先判断“这更像改哪一层”，然后优先去看最可能出手的类。

## 1. 改需求时的总原则

这个项目不要从数据库或 XML 开始改，优先顺序应该是：

1. 先找 `controller` 看接口入口
2. 再找 `service/impl` 看业务规则
3. 再找 `dto / entity / enums`
4. 最后才看 `mapper` 和 SQL

原因：

- 大多数需求变化首先改变的是业务规则，而不是 SQL
- SQL 常常只是最后一层承接

## 2. 常见需求 -> 第一入口

### 改接口入参 / 出参

先看：

- `controller`
- `dto/req`
- `dto/resp`
- `util/Result`

典型场景：

- 登录多传一个字段
- 用户主页多返回一个统计项
- 上传接口返回值新增字段

### 改权限

先看：

- `config/SecurityConfig`
- 对应 `controller` 上的 `@PreAuthorize`
- `service/impl` 里的细粒度校验

典型场景：

- 新增一个只允许管理员访问的接口
- 让作者能看到某类额外数据
- 调整公开文章可见性

### 改业务规则

先看：

- 对应 `ServiceImpl`

典型场景：

- 提交审核的最低字数变了
- 草稿自动摘要规则变了
- 审核退回必须填写更长的原因

### 改查询 / 分页 / 列表排序

先看：

- `mapper`
- `resources/mapper/ArticleMapper.xml`
- 对应 `ServiceImpl`

典型场景：

- 首页 Hero 不想随机了
- 分类页改成按热度排序
- 搜索改成真实 LIKE 或 ES

### 改数据库字段

先看：

- `entity`
- `init.sql`
- `dto`
- `service/impl`

典型场景：

- 用户增加手机号
- 文章增加归档时间
- 审核日志增加备注人昵称

### 改缓存 / Token / 草稿机制

先看：

- `DraftServiceImpl`
- `DraftFlushTask`
- `JwtAuthFilter`
- `JwtHelper`
- `RedisConfig`

## 3. 按模块给出改动入口

### 认证模块

优先文件：

- `AuthController`
- `AuthServiceImpl`
- `JwtHelper`
- `JwtAuthFilter`

高风险点：

- token 生成字段变了，要同时看创建、解析、续期
- 登出逻辑变了，要同时看黑名单和前端 token 清理
- 找回密码逻辑变了，要同时看 Redis TTL 和邮件内容

### 文章创作模块

优先文件：

- `ArticleController`
- `DraftServiceImpl`
- `ArticleServiceImpl`
- `Article`

高风险点：

- 草稿正文和元数据不在同一个存储
- 状态可编辑范围只有 `DRAFT / RETURNED`
- 删除是逻辑删除，不是物理删除

### 审核模块

优先文件：

- `ReviewController`
- `ReviewServiceImpl`
- `ReviewLog`
- `ArticleStatus`
- `ReviewAction`

高风险点：

- 改状态时必须同步考虑日志写入
- 改审核权限时必须同步考虑作者查看日志
- 管理员不能审核自己的文章，这是隐藏业务规则

### 用户主页模块

优先文件：

- `UserController`
- `UserServiceImpl`
- `User`

高风险点：

- “本人视角”和“他人视角”返回不同
- `tab` 过滤只有本人或管理员才能完整生效

### 首页 / 分类 / 搜索模块

优先文件：

- `HomeController`
- `HomeServiceImpl`
- `CategoryController`
- `CategoryServiceImpl`
- `SearchController`
- `SearchServiceImpl`
- `ArticleMapper.xml`

高风险点：

- 首页 Hero 有 Redis 缓存
- 搜索当前是占位实现，别误以为已经是完整功能

### 上传模块

优先文件：

- `UploadController`
- `UploadServiceImpl`
- `WebMvcConfig`
- `application.yml`

高风险点：

- 上传后的 URL 是相对路径
- 静态访问依赖本地磁盘路径映射
- 封面图和其他图片的处理策略不完全一样

## 4. 以后加“文章归档”需求，先改哪里

如果以后要加一个“归档”需求，建议先按这个顺序看：

1. `enums/ArticleStatus`
   - 是否要新增 `ARCHIVED`
2. `init.sql`
   - 数据库枚举和字段是否要扩展
3. `entity/Article`
   - 是否要新增归档时间等字段
4. `ArticleServiceImpl`
   - 哪些状态允许归档
   - 归档后哪些接口还能访问
5. `ReviewServiceImpl` / `UserServiceImpl` / `HomeServiceImpl`
   - 归档文章是否还出现在首页、分类、审核、主页
6. `dto/resp`
   - 前端是否需要拿到归档状态
7. `SecurityConfig`
   - 是否新增管理员归档接口

这类需求不能只改一个 enum，因为它会同时影响：

- 查询过滤
- 状态机
- 权限
- 列表展示
- 前后端 DTO

## 5. 三个最容易踩坑的改动点

### 坑 1：只改路由权限，不改 Service 权限

这个项目很多权限不是只靠 `SecurityConfig`，而是：

- 路由先粗分
- Service 再细分

所以改权限必须两层一起检查。

### 坑 2：只改 MySQL，不管 Redis

特别是草稿系统：

- 正文在 Redis
- 元数据在 MySQL

如果你只改数据库字段或读取逻辑，可能会读到旧正文。

### 坑 3：改状态不改日志

审核相关状态变化如果没写 `review_logs`，前端虽然可能能看到状态，但整个审核历史就断了。

## 6. 改需求前的最小检查清单

每次开始改动前，先问自己 5 个问题：

1. 这个需求改的是接口、权限、业务规则还是查询？
2. 它会不会引入新的状态或影响旧状态流？
3. 它会不会影响 Redis 和 MySQL 的一致性？
4. 它会不会影响前端依赖的 DTO 字段？
5. 它会不会影响审核日志或用户可见范围？

只要这 5 个问题都过一遍，大部分高风险改动都不会漏。
