# 架构约束

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

适合谁看：准备做结构性改动、提取公共模块、增加 Feign 或事件基础设施的人。  
读完能解决什么问题：知道仓库当前有哪些被测试显式约束的规则，避免提交后才被架构测试拦下。

## 约束来源

当前以 [FinalArchitectureTest.java](../../architecture-tests/src/test/java/com/platform/architecture/FinalArchitectureTest.java) 为准。

## 当前被显式测试的规则

### 根 POM 必须保持最终模块集

根 [pom.xml](../../pom.xml) 必须持续包含：

- `platform-kernel`
- `platform-web-support`
- `platform-events`
- `auth-contract`
- `content-contract`
- `review-contract`
- `architecture-tests`

同时不能重新引入 `common` 模块，也不能恢复 `common` 目录。

### 业务服务不能依赖 `common`

所有业务服务的 `pom.xml` 都不能再依赖 `common`。

### `@FeignClient` 只能出现在 contract 模块

如果你新增 Feign client，它必须放在 `auth-contract`、`content-contract` 或 `review-contract` 之类的契约模块中，而不是业务服务实现模块。

### 事件基础设施只能放在 `platform-events`

Outbox、消费日志、RabbitMQ 通用配置和通用执行器等基础设施文件必须放在 `platform-events`，不能散落回业务服务。

### 服务源码不能引用旧共享根包

服务源码不能再引用这类旧前缀：

- `com.platform.common.`
- `com.platform.util.`
- `com.platform.enums.`
- `com.platform.exception.`

### 服务入口必须使用显式边界

测试明确限制：

- 不使用宽泛的 `scanBasePackages`
- 不使用宽泛的 `@EnableFeignClients(basePackages...)`
- 如果启用 Feign，必须走显式 `clients = ...` 声明

## 这些约束背后的目的

- 防止共享模块重新膨胀成“什么都能放”的大杂烩
- 防止服务间实现耦合再次上升
- 保持公共能力、契约和业务实现的边界清晰
- 让目录结构本身就能表达架构意图

## 改结构前建议先做什么

1. 先看是否已有 `platform-*` 或 `*-contract` 可以承载改动
2. 如果想加新共享基础设施，优先判断是否属于 `platform-events` 或 `platform-web-support`
3. 如果想加跨服务调用，先设计契约模块，再改服务入口显式引入
4. 提交前至少跑一次 `architecture-tests`

## 数据与恢复约束

架构测试只保护其显式断言，不证明端到端业务通过。根工程另有 db-migration；迁移需独立预检。会话权威、CAS、决定幂等、Outbox/Inbox 和投影版本约束还需对应模块及真实依赖测试。
