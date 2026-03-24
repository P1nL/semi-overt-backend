# 后端项目理解文档

这组文档面向已经有 Java / Spring Boot 基础、但刚接手这个仓库的读者。目标不是讲框架概念，而是帮助你尽快回答下面这些问题：

- 这个项目的主业务是什么。
- 请求从哪里进，怎么流到 Service、Mapper、MySQL、Redis。
- 哪些接口公开，哪些要求登录，哪些只有管理员能调。
- 文章状态和审核动作在哪里流转。
- 以后要改需求时，第一步该看哪些文件。

建议阅读顺序：

1. [01 项目骨架图](./01-project-map.md)
2. [02 接口与权限矩阵](./02-api-and-permissions-matrix.md)
3. [03 状态机与主业务链路](./03-state-machine-and-main-flows.md)
4. [04 改需求入口清单](./04-change-entry-checklist.md)
5. [05 启动与配置逐文件讲解](./05-boot-and-config-walkthrough.md)
6. [06 认证与请求链路逐文件讲解](./06-request-and-security-walkthrough.md)
7. [07 Controller 逐文件讲解](./07-controller-walkthrough.md)
8. [08 核心 Service 逐文件讲解](./08-core-service-walkthrough.md)
9. [09 数据模型与持久化逐文件讲解](./09-data-and-model-walkthrough.md)
10. [10 支撑模块与公共类型逐文件讲解](./10-supporting-files-walkthrough.md)

优先看的关键源码：

- `src/main/java/com/platform/NowDemoApplication.java`
- `src/main/java/com/platform/config/SecurityConfig.java`
- `src/main/java/com/platform/filter/JwtAuthFilter.java`
- `src/main/java/com/platform/controller/AuthController.java`
- `src/main/java/com/platform/controller/ArticleController.java`
- `src/main/java/com/platform/controller/ReviewController.java`
- `src/main/resources/application.yml`
- `src/main/resources/init.sql`

读完整套文档后，你应该能做到：

- 说出请求从 `Controller -> Service -> Mapper -> MySQL/Redis` 的主链路。
- 手动追踪 `GET /api/v1/home` 和 `POST /api/v1/articles/{id}/submit`。
- 理解登录态的生成、校验、续签、拉黑流程。
- 说清文章从草稿到审核通过的状态变化。
- 判断一个需求更像是改接口、改权限、改业务规则、改状态流，还是改存储层。

补充说明：

- 前四篇是“项目地图”。
- 后六篇是“逐文件讲解”。
- 当前仓库里实际有 9 个 controller，其中 [07 Controller 逐文件讲解](./07-controller-walkthrough.md) 会把新增的 `AdminArticleController` 也讲进去。
