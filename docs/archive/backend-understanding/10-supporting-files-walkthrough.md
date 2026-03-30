# 10 支撑文件逐文件讲解

## 为什么读这篇

很多“代码没问题但系统就是跑不起来”的问题，根源都不在业务类里，而是在脚本、配置样例、反向代理或共享支撑层。这篇专门讲这些文件解决什么问题，以及为什么它们对联调、上线和排障重要。

## 本篇覆盖哪些文件

- `docker-compose.yml`
- `scripts/dev-up.ps1`
- `scripts/dev-down.ps1`
- `scripts/smoke-test.ps1`
- `scripts/run-service.sh`
- `scripts/env/server.env.example`
- `deploy/nginx/now-demo.conf`
- `deploy/nginx/README.md`
- `common` 中的支撑类

## `docker-compose.yml`

文件位置：

- [../../docker-compose.yml](../../docker-compose.yml)

文件职责：

- 在本地一次性拉起 MySQL、Redis、RabbitMQ、Elasticsearch、Nacos 这些中间件

关键行为：

- 让业务服务不必各自内嵌依赖
- 为本地联调提供统一基线环境

依赖关系：

- 被 `scripts/dev-up.ps1` 和本地开发流程依赖

修改风险：

- 端口、用户名、密码改动会直接影响所有本地服务默认配置

常见改动入口：

- 调整本地中间件版本
- 调整挂载目录和端口

## `scripts/dev-up.ps1`

文件位置：

- [../../scripts/dev-up.ps1](../../scripts/dev-up.ps1)

文件职责：

- 在 Windows 本地按顺序启动服务并做健康检查

关键行为：

- 控制启动顺序
- 检查端口监听、`/actuator/health` 和 `/actuator/info`

依赖关系：

- 依赖各模块构建产物和本地中间件

修改风险：

- 健康检查规则改动会直接影响本地联调稳定性

常见改动入口：

- 增加新服务
- 调整启动顺序
- 调整健康检查超时

## `scripts/dev-down.ps1`

文件位置：

- [../../scripts/dev-down.ps1](../../scripts/dev-down.ps1)

文件职责：

- 关闭本地开发启动的服务进程

关键行为：

- 清理本地运行状态，避免端口残留

依赖关系：

- 与 `dev-up.ps1` 配套

修改风险：

- 清理规则写得过宽会误杀无关进程

常见改动入口：

- 新增服务进程管理策略时

## `scripts/smoke-test.ps1`

文件位置：

- [../../scripts/smoke-test.ps1](../../scripts/smoke-test.ps1)

文件职责：

- 执行当前仓库的端到端烟雾验证

关键行为：

- 验证中间件健康
- 验证服务健康接口
- 验证网关公开路由
- 验证无效 token 返回 `401`
- 验证“注册 -> 提审 -> 审核通过 -> 通知 -> 搜索”链路

依赖关系：

- 依赖网关和各业务服务已启动

修改风险：

- 这里不是随便写脚本，它定义了当前仓库“主链路可用”的最低验收线

常见改动入口：

- 新增主链路断言
- 修复脚本兼容性问题

## `scripts/run-service.sh`

文件位置：

- [../../scripts/run-service.sh](../../scripts/run-service.sh)

文件职责：

- 作为 Linux 主机上的 `java -jar` 启动脚本

关键行为：

- 加载环境变量
- 启动指定服务 Jar
- 支持服务化运行的基本参数组织

依赖关系：

- 依赖构建出的 Jar
- 依赖 `server.env`

修改风险：

- 这是正式发布基线脚本，改错会直接影响线上可启动性

常见改动入口：

- 调整 JVM 参数
- 调整日志目录和运行参数

## `scripts/env/server.env.example`

文件位置：

- [../../scripts/env/server.env.example](../../scripts/env/server.env.example)

文件职责：

- 提供 Linux 启动所需环境变量样例

关键行为：

- 集中列出数据库、Redis、RabbitMQ、Nacos、JWT、文件存储等关键环境变量

依赖关系：

- 被 `run-service.sh` 使用

修改风险：

- 如果样例和真实代码要求脱节，新同学最容易按错变量名

常见改动入口：

- 新增跨服务必需环境变量

## `deploy/nginx/now-demo.conf`

文件位置：

- [../../deploy/nginx/now-demo.conf](../../deploy/nginx/now-demo.conf)

文件职责：

- 为生产或预发环境提供可选的外层 Nginx 入口配置

关键行为：

- 反向代理到网关
- 处理静态资源或外层 HTTP 入口规则

依赖关系：

- 依赖网关服务的可访问地址

修改风险：

- Nginx 路由改错会让外部访问与服务实际路由不一致

常见改动入口：

- 切换正式域名
- 增加 HTTPS 和反向代理规则

## `deploy/nginx/README.md`

文件位置：

- [../../deploy/nginx/README.md](../../deploy/nginx/README.md)

文件职责：

- 解释当前 Nginx 配置如何使用

关键行为：

- 帮助运维或接手同学理解 Nginx 不在当前仓库里承担业务逻辑，只承担外层入口职责

依赖关系：

- 与 `now-demo.conf` 配套

修改风险：

- 文档与配置不一致时，上线人员最容易执行错误步骤

常见改动入口：

- 更新域名、证书或代理说明

## `HeaderNames`

文件位置：

- [../../common/src/main/java/com/platform/common/constant/HeaderNames.java](../../common/src/main/java/com/platform/common/constant/HeaderNames.java)

文件职责：

- 统一定义跨服务内部头协议

关键行为：

- 保证网关写什么、下游读什么是一致的

依赖关系：

- 被网关、服务安全层、Feign 透传共同使用

修改风险：

- 属于跨服务协议，轻易不要改名

常见改动入口：

- 新增内部头

## `EventConstants`

文件位置：

- [../../common/src/main/java/com/platform/common/constant/EventConstants.java](../../common/src/main/java/com/platform/common/constant/EventConstants.java)

文件职责：

- 统一定义事件类型、交换机路由和队列名

关键行为：

- 约束内容、审核、通知、搜索之间的事件协作

依赖关系：

- 被多个服务的 MQ 生产和消费代码共享

修改风险：

- 它是异步链路协议面，变更必须检查所有生产者和消费者

常见改动入口：

- 新增事件队列
- 调整事件消费者标识

## `RabbitEventConfig`

文件位置：

- [../../common/src/main/java/com/platform/common/config/RabbitEventConfig.java](../../common/src/main/java/com/platform/common/config/RabbitEventConfig.java)

文件职责：

- 统一声明 RabbitMQ 事件基础设施配置

关键行为：

- 根据事件常量构造交换机、队列、重试队列、死信队列等基础拓扑

依赖关系：

- 被内容、审核、通知、搜索这些依赖 MQ 的服务使用

修改风险：

- 这里的拓扑一旦变化，最容易出现“消息发了但没人消费”或“消息无限重试”

常见改动入口：

- 新增事件路由
- 调整重试与死信策略

## `EventOutboxService`

文件位置：

- [../../common/src/main/java/com/platform/common/support/EventOutboxService.java](../../common/src/main/java/com/platform/common/support/EventOutboxService.java)

文件职责：

- 提供事件发件箱写入能力

关键行为：

- 在本地事务内先落一条待发布事件记录，再由异步发布器发送到 MQ

依赖关系：

- 被内容域、审核域等需要可靠发事件的服务使用

修改风险：

- 发件箱是最终一致性的关键支点，改坏后最容易出现“数据库状态改了，但事件没出去”

常见改动入口：

- 调整发件箱记录字段
- 新增通用事件写入能力

## `OutboxPublisherSupport`

文件位置：

- [../../common/src/main/java/com/platform/common/support/OutboxPublisherSupport.java](../../common/src/main/java/com/platform/common/support/OutboxPublisherSupport.java)

文件职责：

- 封装发件箱记录的批量发布通用逻辑

关键行为：

- 查询待发送事件
- 发布到 MQ
- 回写发送结果

依赖关系：

- 被各服务自己的 outbox publisher 任务使用

修改风险：

- 发布批次和失败处理改动会联动所有发件箱型服务

常见改动入口：

- 调整批量大小
- 调整发布失败处理

## `EventConsumeService`

文件位置：

- [../../common/src/main/java/com/platform/common/support/EventConsumeService.java](../../common/src/main/java/com/platform/common/support/EventConsumeService.java)

文件职责：

- 提供消费去重、消费日志和幂等支持

关键行为：

- 记录某个消费者是否已经处理过某条事件

依赖关系：

- 被通知、搜索、内容、审核等消费方共用

修改风险：

- 改坏幂等逻辑会导致重复通知、重复建索引或重复落状态

常见改动入口：

- 调整消费日志保留或幂等键策略

## 为什么这些文件重要

- 它们决定系统能否稳定启动
- 它们决定联调和上线有没有统一脚本与统一基线
- 它们决定跨服务事件和内部协议是不是可维护

业务类看的是“想做什么”，这些支撑文件解决的是“怎样稳定地做成、跑起来、排查掉”。
