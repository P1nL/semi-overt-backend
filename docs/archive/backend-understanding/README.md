# backend-understanding 归档说明

这是历史单体/早期拆分阶段的项目理解文档归档区，不再作为当前仓库主入口。

## 这组文档的编写背景

这批文档最初围绕根目录单体 `src/` 和早期后端结构整理，用来帮助读者理解单体代码和改造前后的业务链路。

## 现在为什么归档

当前仓库的真实运行主实现已经切换为 Maven 多模块微服务结构：

- `gateway-service`
- `auth-service`
- `content-service`
- `review-service`
- `search-service`
- `file-service`
- `notification-service`
- `common`

因此这组文档中的很多入口路径、模块职责、代码位置已经不是当前事实。

## 还值得参考的内容

- 早期业务语义解释
- 单体阶段的需求理解方式
- 一些状态机和接口语义的历史背景

## 哪些内容已经不是当前事实

- 把根目录 `src/main/java/...` 当成当前运行主实现
- 把项目整体描述为单体
- 以单模块启动入口作为默认运行方式

## 使用建议

如果你要理解当前仓库，请优先看新的主文档目录：

- [项目概览](../../01-overview/01-project-overview.md)
- [模块地图](../../01-overview/02-module-map.md)
- [后端导读](../../02-backend-guide/01-api-and-auth.md)
- [运行与交付](../../03-runtime-and-delivery/01-local-development.md)
