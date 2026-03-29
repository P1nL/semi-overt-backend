# 02 接口与权限矩阵

## 1. 统一规则

接口统一前缀基本是：

- `/api/v1/auth`
- `/api/v1/home`
- `/api/v1/categories`
- `/api/v1/articles`
- `/api/v1/reviews`
- `/api/v1/users`
- `/api/v1/uploads`
- `/api/v1/search`

统一返回格式：

- 成功：`code=200`
- 失败：`code=400/401/403/404/409/429/500`

权限来源有两层：

1. `SecurityConfig` 的路由级规则
2. `@PreAuthorize` 和 Service 内部的细粒度校验

## 2. 公开 / 登录 / 管理员矩阵

| 模块 | 接口 | 方法 | 访问级别 | 主要作用 | 主服务 |
| --- | --- | --- | --- | --- | --- |
| 首页 | `/api/v1/home` | `GET` | 公开 | 首页 Hero 和分区聚合 | `HomeServiceImpl` |
| 分类 | `/api/v1/categories/{category}/articles` | `GET` | 公开 | 分类文章分页 | `CategoryServiceImpl` |
| 搜索 | `/api/v1/search/articles` | `GET` | 公开 | 基础关键词搜索，数据来自 Elasticsearch 文章投影 | `SearchServiceImpl` |
| 认证 | `/api/v1/auth/register` | `POST` | 公开 | 注册并直接返回 token | `AuthServiceImpl` |
| 认证 | `/api/v1/auth/login` | `POST` | 公开 | 登录并返回 token | `AuthServiceImpl` |
| 认证 | `/api/v1/auth/forgot-password` | `POST` | 公开 | 发送重置密码邮件 | `AuthServiceImpl` |
| 认证 | `/api/v1/auth/reset-password` | `POST` | 公开 | 使用邮件 token 重置密码 | `AuthServiceImpl` |
| 文章 | `/api/v1/articles/{articleId}` | `GET` | 公开入口，内部细分 | 获取文章详情 | `ArticleServiceImpl` |
| 用户主页 | `/api/v1/users/{username}/profile` | `GET` | 公开入口，内部细分 | 查看公开主页或本人主页 | `UserServiceImpl` |
| 认证 | `/api/v1/auth/logout` | `POST` | 已登录 | token 拉黑 | `AuthServiceImpl` |
| 文章 | `/api/v1/articles` | `POST` | 已登录 | 新建空草稿 | `ArticleServiceImpl` |
| 文章 | `/api/v1/articles/{articleId}/draft` | `PUT` | 已登录且本人 | 自动保存草稿 | `DraftServiceImpl` |
| 文章 | `/api/v1/articles/drafts` | `GET` | 已登录 | 草稿箱列表 | `DraftServiceImpl` |
| 文章 | `/api/v1/articles/{articleId}/submit` | `POST` | 已登录且本人 | 提交审核 | `ArticleServiceImpl` |
| 文章 | `/api/v1/articles/{articleId}/cancel-review` | `POST` | 已登录且本人 | 取消审核 | `ArticleServiceImpl` |
| 文章 | `/api/v1/articles/{articleId}` | `DELETE` | 已登录且本人 | 删除文章 | `ArticleServiceImpl` |
| 当前用户 | `/api/v1/users/me` | `GET` | 已登录 | 获取当前用户资料 | `UserServiceImpl` |
| 当前用户 | `/api/v1/users/me/profile` | `PUT` | 已登录 | 修改资料 | `UserServiceImpl` |
| 上传 | `/api/v1/uploads/images` | `POST` | 已登录 | 上传图片 | `UploadServiceImpl` |
| 审核日志 | `/api/v1/reviews/{articleId}/logs` | `GET` | 已登录，内部细分 | 作者查看自己的审核日志，管理员可看全部 | `ReviewServiceImpl` |
| 审核队列 | `/api/v1/reviews/pending` | `GET` | 管理员 | 获取待审核列表 | `ReviewServiceImpl` |
| 审核动作 | `/api/v1/reviews/{articleId}/action` | `POST` | 管理员 | 通过 / 退回 / 拒绝 | `ReviewServiceImpl` |

## 3. 8 个 Controller 的职责边界

### `AuthController`

- 只处理认证生命周期
- 不负责权限判断
- token 实际生成、黑名单、重置密码 token 都在 `AuthServiceImpl`

### `HomeController`

- 只有一个首页聚合入口
- 负责把首页展示所需的多块数据一次返回给前端

### `CategoryController`

- 只负责按阅读时长分类做分页列表
- 不负责搜索或详情

### `SearchController`

- 独立于分类页的搜索入口
- 当前实现为基础 Elasticsearch 搜索，接口 shape 已稳定
- 搜索范围固定为标题和摘要，只返回已发布文章投影

### `ArticleController`

- 作者工作台核心入口
- 覆盖创建、保存草稿、草稿箱、详情、提交审核、取消审核、删除

### `ReviewController`

- 管理员工作台核心入口
- 覆盖待审核队列、审核动作、审核日志

### `UserController`

- 覆盖当前用户资料和公开主页
- “我的资料”和“别人的主页”是同一模块的两类视图

### `UploadController`

- 只负责图片上传
- 物理存储和主色提取在 `UploadServiceImpl`

## 4. 特别容易误判的权限点

### 文章详情不是“完全公开”

虽然 `GET /api/v1/articles/{articleId}` 在路由级是公开入口，但真正能否看到文章由 `ArticleServiceImpl.checkReadPermission` 决定：

- `APPROVED`：所有人可见
- `PENDING`：作者本人和管理员可见
- `DRAFT / RETURNED / REJECTED`：只有作者本人可见

### 用户主页不是“完全相同的视图”

`GET /api/v1/users/{username}/profile` 的返回会因为访问者身份不同而变化：

- 游客或他人访问：只能看到 `APPROVED`
- 本人或管理员访问：可以按 `tab` 查看不同状态

### 审核日志是一个特殊接口

`/api/v1/reviews/**` 整体偏管理员接口，但 `GET /api/v1/reviews/{articleId}/logs` 允许普通已登录作者访问自己的日志，真正权限在 `ReviewServiceImpl` 里二次判断。

## 5. 接口层对前端最重要的两个约束

### 约束 1：不要只看 HTTP 状态码

这个项目很多错误会以 `HTTP 200 + Result.fail(...)` 的形式返回。

所以前端必须看：

- `response.data.code`
- `response.data.message`

### 约束 2：要处理 `New-Token`

当旧 token 快到期时，后端会在响应头下发：

- `New-Token`

前端应该把它覆盖到本地 token。

## 6. 接口矩阵怎么用来读代码

如果你是为了改需求，建议用下面的方法：

- 先在这个矩阵里找到目标接口
- 再看对应 controller
- 再直接跳 `service/impl`
- 最后才看 `mapper` 和 SQL

这样不会一开始就陷进细节。
