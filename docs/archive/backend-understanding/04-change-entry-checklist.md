# 04 改需求入口清单

## 为什么读这篇

这篇不是讲业务流程，而是讲“你要改某类需求时，第一站应该进哪个模块、看哪些文件”。它的价值在于减少误入歧途，比如明明是内容状态问题，却先去改审核服务；明明是共享契约变化，却只改单个模块。

## 改接口

本质是在改“谁对外暴露什么能力、返回什么契约”。

先看：

- 网关路由与入口控制是否需要变化
- 对应业务服务的 Controller
- 对应 Service
- 统一返回模型和异常处理

常见入口：

- 公网入口先看 `gateway-service`
- 业务语义再看具体服务的 Controller 和 Service
- 如果返回字段跨服务共用，再看 `common`

## 改权限

本质是在改“哪些人能访问什么资源、在什么地方完成拦截”。

先看：

- `gateway-service` 的鉴权过滤与路由放行规则
- 各服务的 `SecurityConfig`
- 内部头和用户上下文读取方式

不要先在 Controller 里硬写 if/else。那样会把权限逻辑写散，后续很难统一维护。

## 改状态流

本质是在改“哪个服务拥有状态真源、状态如何推进、事件在什么时机发出”。

先看：

- `content-service` 的文章状态落库逻辑
- `review-service` 的审核决定逻辑
- `common` 的事件模型与事件常量

如果一项改动涉及文章状态新增、状态迁移规则变化或提审节流变化，起点几乎一定是 `content-service`。

## 改搜索

本质是在改“公开搜索暴露什么、索引里存什么、事件怎样同步投影”。

先看：

- `search-service` 的 `SearchController`
- `SearchServiceImpl`
- `SearchEventServiceImpl`
- `SearchIndexSyncServiceImpl`
- 搜索文档和 Repository

如果你想改的是“哪些文章能被搜到”，优先看索引写入阶段，而不是只看查询条件。

## 改通知

本质是在改“状态变化后给谁发什么提示，以及如何记录这次投递”。

先看：

- `notification-service` 的事件监听与事件服务
- 通知实体、投递记录实体与 Mapper
- `common` 里的文章状态变更事件定义

不要把通知文案或通知生成逻辑塞回内容域，通知属于派生消费结果。

## 改上传

本质是在改“文件能不能收、落到哪里、外部怎样访问到它”。

先看：

- `file-service` 的 `UploadController`
- `UploadServiceImpl`
- `StorageConfig`
- `WebMvcConfig`

如果改的是对象存储接入，那不只是改 Controller，而是要整体替换当前的本地磁盘策略。

## 改部署

本质是在改“服务如何启动、配置从哪里来、验证和回滚怎么做”。

先看：

- 根 `pom.xml`
- 各模块 `application.yml`
- `scripts/dev-up.ps1`
- `scripts/run-service.sh`
- `scripts/smoke-test.ps1`
- `docs/03-runtime-and-delivery`

## 为什么跨服务改动先看 `common`

`common` 放的是跨服务共享契约。它不是“顺手放点工具类”的地方，而是会直接影响多个服务编译和行为的一层。

先看 `common` 的典型场景：

- 新增或修改事件模型
- 修改内部 DTO
- 修改内部头名称
- 调整共享异常、统一返回或上下文传递逻辑

风险也最明显：

- 一处契约变化可能让多个服务同时失配
- 编译能过，不代表运行时兼容
- MQ 消费、Feign 调用、内部头过滤都可能被联动打断

## 一句话判断法

- 改“谁能进来”先看网关和安全配置
- 改“文章怎么流转”先看内容域
- 改“审核怎么决定”先看审核域
- 改“搜索怎么可见”先看搜索域
- 改“通知怎么生成”先看通知域
- 改“共享契约”先看 `common`
