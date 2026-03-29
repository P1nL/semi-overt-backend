# distributed-refactor 归档说明

这是分布式改造过程文档归档区，不再作为当前仓库主文档入口。

## 这组文档的编写背景

这批文档最初用于规划“从单体到多模块微服务”的拆分过程，重点是：

- 为什么拆服务
- 先做什么
- 事件、通知、搜索怎么接起来
- 如何分线程推进改造

## 现在为什么归档

当前仓库已经不再处于纯规划阶段，而是已经落地了：

- `gateway-service`
- `auth-service`
- `content-service`
- `review-service`
- `search-service`
- `file-service`
- `notification-service`
- `common`

因此这组文档中大量“计划做什么”“后续要接什么”的语气，已经不适合作为当前事实文档继续对外暴露。

## 还值得参考的内容

- 服务拆分的历史决策背景
- 事件驱动和派生能力设计的思路来源
- 本地交付与运维基线形成过程

## 哪些内容已经不是当前事实

- 把搜索、通知等能力描述成未落地
- 把改造线程提示词包当成当前开发入口
- 把未来拆分方案当成当前正式结构

## 使用建议

当前仓库的正式阅读入口已经迁移到新的主文档目录：

- [项目概览](../../01-overview/01-project-overview.md)
- [模块地图](../../01-overview/02-module-map.md)
- [核心业务链路](../../01-overview/03-main-flows.md)
- [上线流程 Runbook](../../03-runtime-and-delivery/03-release-runbook.md)
