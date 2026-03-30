# backend-understanding

这组文档是当前仓库的逐文件详细导读，适合刚接手项目、准备改需求、排查链路问题或梳理上线风险的后端同学。

## 为什么沿用旧路径

这组文档沿用了 `docs/archive/backend-understanding` 这个旧路径，但内容已经全部更新为当前事实。这样做是为了保留既有链接入口，同时避免再额外制造一套新的“文件导读目录”。

你可以把它理解为：

- 路径是旧的
- 内容是新的
- 讲解对象是当前 Maven 多模块微服务仓库，而不是已经移除的旧单体根目录

## 推荐阅读顺序

1. [01 项目骨架图](./01-project-map.md)
2. [02 API 与权限矩阵](./02-api-and-permissions-matrix.md)
3. [03 状态机与主链路](./03-state-machine-and-main-flows.md)
4. [04 改需求入口清单](./04-change-entry-checklist.md)
5. [05 启动与配置逐文件讲解](./05-boot-and-config-walkthrough.md)
6. [06 请求链路与安全逐文件讲解](./06-request-and-security-walkthrough.md)
7. [07 Controller 逐文件讲解](./07-controller-walkthrough.md)
8. [08 Core Service 逐文件讲解](./08-core-service-walkthrough.md)
9. [09 数据与模型逐文件讲解](./09-data-and-model-walkthrough.md)
10. [10 支撑文件逐文件讲解](./10-supporting-files-walkthrough.md)

## 覆盖范围

这组文档覆盖当前运行主实现中的关键文件：

- 网关入口、鉴权、路由和限流
- 认证服务的注册、登录、用户资料和内部用户接口
- 内容服务的首页、分类、文章、草稿、提审和内部内容接口
- 审核服务的审核任务、审核日志、审核事件与任务投影
- 搜索服务的公开搜索、事件索引、启动回填与 ES 文档
- 文件服务的上传、落盘和访问映射
- 通知服务的事件消费、通知落库与投递记录
- `common` 里的共享契约、事件常量、内部头协议和公共支持代码
- 根 `pom.xml`、各模块 `application.yml`、本地脚本、Linux 启动脚本与 Nginx 配置

## 阅读原则

- 这组文档讲的是“当前运行主链路中的关键文件”，不是给每个简单 DTO、枚举和样板类都写一页。
- 每个关键文件都会尽量说明五件事：文件位置、文件职责、关键行为、依赖关系、修改风险。
- 文档里如果提到旧单体 `src/`，都只是在解释历史背景，不代表当前仓库仍保留该目录。
