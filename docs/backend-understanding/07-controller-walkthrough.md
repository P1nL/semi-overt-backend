# Controller 逐文件讲解

这一篇只看接口入口，不深入业务实现。目标是先搞清楚每个 Controller 的职责边界和它依赖哪个 Service。

## 先看整体分工

当前仓库里实际有 9 个 Controller：

- `AuthController`
- `HomeController`
- `CategoryController`
- `SearchController`
- `ArticleController`
- `ReviewController`
- `UserController`
- `UploadController`
- `AdminArticleController`

它们的共同特征是：

- 只收参数
- 调 Service
- 包 `Result`
- 很少自己写业务逻辑

## 1. `AuthController.java`

文件：`src/main/java/com/platform/controller/AuthController.java`

职责：

- 认证相关入口
- 注册、登录、登出、忘记密码、重置密码

依赖：

- `AuthService`

关键接口：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

你读它时要注意：

- 登录、注册的入参用 `req DTO`，返回统一是 `AuthResp`
- `logout` 自己从请求头里提取 token，再交给 Service 拉黑
- 这里不做密码校验逻辑，真正逻辑都在 `AuthServiceImpl`

改需求时先看这里的场景：

- 登录入参结构变化
- 注册接口返回字段变化
- 新增认证相关接口

## 2. `HomeController.java`

文件：`src/main/java/com/platform/controller/HomeController.java`

职责：

- 首页聚合接口

依赖：

- `HomeService`

关键接口：

- `GET /api/v1/home`

特点：

- 公开接口
- Controller 极薄，说明首页组装逻辑全部在 Service

## 3. `CategoryController.java`

文件：`src/main/java/com/platform/controller/CategoryController.java`

职责：

- 分类页文章列表

依赖：

- `CategoryService`

关键接口：

- `GET /api/v1/categories/{category}/articles`

特点：

- `category` 实际上传的是 `QUICK / SHORT / DEEP`
- `page / pageSize` 是典型分页接口

## 4. `SearchController.java`

文件：`src/main/java/com/platform/controller/SearchController.java`

职责：

- 搜索入口

依赖：

- `SearchService`

关键接口：

- `GET /api/v1/search/articles`

特点：

- 参数校验已经加了 `@NotBlank`
- 当前 Service 还是占位实现，Controller 已经稳定

这类设计的价值是：以后替换搜索实现时，前端 API 不用改。

## 5. `ArticleController.java`

文件：`src/main/java/com/platform/controller/ArticleController.java`

职责：

- 作者侧的文章与草稿操作入口

依赖：

- `ArticleService`
- `DraftService`

关键接口：

- `POST /api/v1/articles`
- `PUT /api/v1/articles/{articleId}/draft`
- `GET /api/v1/articles/drafts`
- `GET /api/v1/articles/{articleId}`
- `POST /api/v1/articles/{articleId}/submit`
- `POST /api/v1/articles/{articleId}/cancel-review`
- `DELETE /api/v1/articles/{articleId}`

它是主业务最核心的 Controller 之一。

你读它时要形成这个分层感：

- “创建、提交、取消审核、删除”走 `ArticleService`
- “保存草稿、草稿箱”走 `DraftService`

这里的好处是：

- 文章正式状态流和草稿缓存逻辑被拆开了
- 以后改草稿保存，不一定会动提审逻辑

## 6. `ReviewController.java`

文件：`src/main/java/com/platform/controller/ReviewController.java`

职责：

- 审核侧操作入口

依赖：

- `ReviewService`

关键接口：

- `GET /api/v1/reviews/pending`
- `POST /api/v1/reviews/{articleId}/action`
- `GET /api/v1/reviews/{articleId}/logs`

需要特别注意的点：

- 审核动作只包含 `APPROVE / RETURN / REJECT`
- `CANCEL` 不是管理员动作，是作者取消审核
- 日志接口理论上允许文章作者查看，但还要结合 `SecurityConfig` 一起读

## 7. `UserController.java`

文件：`src/main/java/com/platform/controller/UserController.java`

职责：

- 当前登录用户信息
- 用户资料修改
- 用户主页

依赖：

- `UserService`

关键接口：

- `GET /api/v1/users/me`
- `PUT /api/v1/users/me/profile`
- `GET /api/v1/users/{username}/profile`

这个 Controller 的关键在于“同一个用户主页接口，对本人和访客返回的数据范围不同”。这种差异化不是 Controller 做的，而是 Service 做的。

## 8. `UploadController.java`

文件：`src/main/java/com/platform/controller/UploadController.java`

职责：

- 图片上传入口

依赖：

- `UploadService`

关键接口：

- `POST /api/v1/uploads/images`

特点：

- 使用 `multipart/form-data`
- 参数除了文件，还有 `bizType`
- 真正的文件校验、扩展名检查、图片解析、路径生成都不在 Controller

## 9. `AdminArticleController.java`

文件：`src/main/java/com/platform/controller/AdminArticleController.java`

职责：

- 管理员删除文章

依赖：

- `ArticleService`

关键接口：

- `DELETE /api/v1/admin/articles/{articleId}`

为什么它单独存在：

- 作者删除文章和管理员删除文章属于两套规则
- 共用 `ArticleService`，但接口入口分开更清晰

## 10. 看 Controller 时的读法

读每个 Controller，不要一上来钻方法体。先按下面顺序看：

1. `@RequestMapping` 基础路径
2. 注释里的接口清单
3. 依赖了哪些 Service
4. 哪些方法有 `@PreAuthorize`
5. 哪些方法会拿 `currentUserId`
6. 入参是哪个 `req DTO`
7. 出参是哪个 `resp DTO`

这样你会先有“接口地图”，再去看 Service 细节，不会迷路。
