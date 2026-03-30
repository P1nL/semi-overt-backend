# 07 Controller 逐文件讲解

## 为什么读这篇

这篇用来回答“请求真正落到哪个 Controller、Controller 只该做什么、不该做什么”。当前仓库已经不是单体 Controller 大杂烩，而是按服务拆分的入口层。你改接口时，通常第一眼要看的就是这里。

## 本篇覆盖哪些文件

- `gateway-service`：网关侧辅助入口
- `auth-service`：认证与用户 Controller
- `content-service`：首页、分类、文章与内部文章接口
- `review-service`：审核与内部审核接口
- `search-service`：公开搜索接口
- `file-service`：上传接口

## `GatewayAuthController`

文件位置：

- [../../gateway-service/src/main/java/com/platform/gateway/controller/GatewayAuthController.java](../../gateway-service/src/main/java/com/platform/gateway/controller/GatewayAuthController.java)

文件职责：

- 提供网关侧的认证辅助入口和调试性入口
- 它不替代 `auth-service` 的真实认证业务

关键接口：

- 这类接口通常围绕网关可见的身份判断或公共入口校验展开

依赖的 Service：

- 依赖网关安全组件，而不是直接承载用户域业务

修改风险：

- 很容易因为“图方便”把本该属于 `auth-service` 的能力加到网关层

常见改动入口：

- 需要补网关层认证辅助返回时

## `AuthController`

文件位置：

- [../../auth-service/src/main/java/com/platform/controller/AuthController.java](../../auth-service/src/main/java/com/platform/controller/AuthController.java)

文件职责：

- 提供注册、登录、找回密码和重置密码等认证入口

关键接口：

- 注册
- 登录
- 忘记密码
- 重置密码

依赖的 Service：

- `AuthServiceImpl`

修改风险：

- 这里改的不只是接口返回，还会影响 JWT 生成、Redis 临时数据、邮件链路

常见改动入口：

- 调整注册校验
- 调整登录返回
- 调整找回密码流程

## `UserController`

文件位置：

- [../../auth-service/src/main/java/com/platform/controller/UserController.java](../../auth-service/src/main/java/com/platform/controller/UserController.java)

文件职责：

- 提供当前登录用户的资料查询和更新接口

关键接口：

- 查看个人资料
- 更新昵称、头像或其他用户资料字段

依赖的 Service：

- `UserServiceImpl`

修改风险：

- 用户资料字段是多个页面会复用的数据，改返回字段要注意兼容前端和搜索作者补全链路

常见改动入口：

- 新增用户资料字段
- 调整资料更新规则

## `InternalUserController`

文件位置：

- [../../auth-service/src/main/java/com/platform/controller/internal/InternalUserController.java](../../auth-service/src/main/java/com/platform/controller/internal/InternalUserController.java)

文件职责：

- 向其他服务提供内部用户摘要接口

关键接口：

- 按用户 ID 批量或单个查询用户摘要

依赖的 Service：

- `UserServiceImpl`

修改风险：

- 这是跨服务契约接口，改字段或改路径会直接影响内容、搜索、通知等依赖方

常见改动入口：

- 需要给其他服务新增稳定的用户摘要字段时

## `HomeController`

文件位置：

- [../../content-service/src/main/java/com/platform/controller/HomeController.java](../../content-service/src/main/java/com/platform/controller/HomeController.java)

文件职责：

- 提供首页文章流和首页展示所需的内容入口

关键接口：

- 首页推荐或最新文章列表

依赖的 Service：

- `HomeServiceImpl`

修改风险：

- 首页接口很容易被误改成“顺便做搜索、顺便做筛选”的大杂烩

常见改动入口：

- 首页卡片字段变化
- 首页排序或分页变化

## `CategoryController`

文件位置：

- [../../content-service/src/main/java/com/platform/controller/CategoryController.java](../../content-service/src/main/java/com/platform/controller/CategoryController.java)

文件职责：

- 提供分类查询与分类相关内容入口

关键接口：

- 分类列表
- 分类下文章列表或分类详情相关接口

依赖的 Service：

- `CategoryServiceImpl`

修改风险：

- 分类语义如果与首页、文章详情使用的不一致，会导致前端多处显示错位

常见改动入口：

- 分类展示字段调整
- 分类排序调整

## `ArticleController`

文件位置：

- [../../content-service/src/main/java/com/platform/controller/ArticleController.java](../../content-service/src/main/java/com/platform/controller/ArticleController.java)

文件职责：

- 提供文章创建、编辑、保存草稿、详情、提审等核心入口

关键接口：

- 新建文章
- 保存草稿
- 查询文章详情
- 提交审核
- 查询自己的文章列表

依赖的 Service：

- `ArticleServiceImpl`
- 某些草稿相关能力会进一步依赖 `DraftServiceImpl`

修改风险：

- 这里的任何状态相关改动都有可能影响审核、通知和搜索链路

常见改动入口：

- 文章字段变化
- 提审前校验变化
- 草稿相关接口变化

## `InternalArticleController`

文件位置：

- [../../content-service/src/main/java/com/platform/controller/internal/InternalArticleController.java](../../content-service/src/main/java/com/platform/controller/internal/InternalArticleController.java)

文件职责：

- 向其他服务暴露内容域内部接口

关键接口：

- 供审核、搜索、通知或其他内部链路读取最小必要文章信息

依赖的 Service：

- `ArticleServiceImpl`

修改风险：

- 内部接口一旦承载过多展示字段，就会把内容域和调用方绑得太紧

常见改动入口：

- 新增跨服务最小文章摘要字段

## `ReviewController`

文件位置：

- [../../review-service/src/main/java/com/platform/controller/ReviewController.java](../../review-service/src/main/java/com/platform/controller/ReviewController.java)

文件职责：

- 提供管理员审核任务查询和审核决定入口

关键接口：

- 查询待审任务
- 审核通过
- 审核退回
- 审核拒绝

依赖的 Service：

- `ReviewServiceImpl`
- `ReviewTaskServiceImpl`

修改风险：

- 这里的决定动作如果直接改内容库而绕过既有链路，会破坏领域边界

常见改动入口：

- 新增审核动作
- 调整审核列表筛选

## `InternalReviewController`

文件位置：

- [../../review-service/src/main/java/com/platform/controller/internal/InternalReviewController.java](../../review-service/src/main/java/com/platform/controller/internal/InternalReviewController.java)

文件职责：

- 暴露审核域内部读取接口

关键接口：

- 查询审核结果或审核任务摘要这类内部能力

依赖的 Service：

- `ReviewTaskServiceImpl` 或 `ReviewServiceImpl`

修改风险：

- 内部接口容易被误当成公网接口使用，权限和路由边界要看清

常见改动入口：

- 其他服务需要读取审核侧投影信息时

## `SearchController`

文件位置：

- [../../search-service/src/main/java/com/platform/controller/SearchController.java](../../search-service/src/main/java/com/platform/controller/SearchController.java)

文件职责：

- 提供公开文章搜索入口

关键接口：

- `GET /api/v1/search/articles`

依赖的 Service：

- `SearchServiceImpl`

修改风险：

- 这里改参数或返回结构会直接影响前端搜索页和 smoke 验证

常见改动入口：

- 调整搜索请求参数
- 调整搜索结果卡片字段

## `UploadController`

文件位置：

- [../../file-service/src/main/java/com/platform/controller/UploadController.java](../../file-service/src/main/java/com/platform/controller/UploadController.java)

文件职责：

- 提供文件上传入口

关键接口：

- 上传图片或其他允许类型文件

依赖的 Service：

- `UploadServiceImpl`

修改风险：

- 上传接口改动通常会联动文件校验、物理存储和访问 URL 规则

常见改动入口：

- 放宽或收紧允许文件类型
- 调整返回 URL 结构

## Controller 层应该保持什么边界

- Controller 负责收参与出参，不负责写长业务流程
- 真正的状态推进、事务、事件发送和外部依赖协调应下沉到 Service
- 内部接口要坚持“最小必要信息”原则
- 改接口前先判断是公网接口还是内部接口，再决定改动范围
