# semi-overt 文档中心

更新日期：2026-09-15。本文档集对应 semi-overt-backend 微服务仓库，项目展示名称统一为 **semi-overt**。

| 目的 | 入口 |
| --- | --- |
| 初次接手、运行 | [起步](01-start-here/README.md) |
| 模块、状态与事件 | [架构](02-architecture/README.md) |
| 开发、配置、部署、排障 | [开发与运维](03-development-and-operations/README.md) |
| API、内部契约、端口、脚本 | [参考手册](04-reference/README.md) |
| 历史与代码导读 | [附录](05-appendices/README.md) |
| 前后端协作与登录 | [全栈指南](06-fullstack-guide/README.md) |
| 模型配置与编辑器限制 | [AI 润色](ai-polish.md) |
| 全 Docker 和离线目标机操作 | [Docker 交付手册](../deploy/docker/README.md) |
| S0–S5 契约与回执 | [阶段索引](sync/README.md) |
| 旧设计和旧手册 | [归档](archive/README.md) |

## 三种环境不能混用

| 模式 | 脚本 | 默认端口 | 用途 |
| --- | --- | --- | --- |
| 传统本地 | scripts/dev-up.ps1 | 网关 8080，业务 8081–8086 | 保留的开发入口 |
| S5 隔离联调 | scripts/s5-env.ps1 start | 网关 18080，业务 18081–18086 | 本机 Java + 隔离 Docker 中间件 |
| 全 Docker | scripts/docker-demo.ps1 up | 应用 18000，网关 18080，Mailpit 18025 | 前端、迁移、后端和中间件均容器化 |

S5 与全 Docker 默认冲突于 18080，不能直接同时启动。S5 配套演示前端是独立的 semi-overt-frontend 仓库；15173 是演示联调端口，不是容器应用端口。不要误操作生产前端或旧单体仓库。

## 命名与事实边界

- 项目名使用 semi-overt，弃用 now / now-demo 作为现行展示名称。
- 源码仍使用 now-demo-parent Maven 坐标、deploy/nginx/now-demo.conf 等历史技术标识。本轮不改构建坐标、配置文件名、容器、数据库或持久化数据；文档引用保留真实技术名称，避免产生不可执行的命令。
- 当前源码和配置优先于手册；有日期的阶段回执仅证明当时的验证范围，归档不能作为现行操作指南。
- 本次只核对源码与文档，未重新运行服务、迁移、付费模型或整站验收，不代表当前 HEAD、已发布镜像或生产状态已验收。
- 历史 .runtime 回执可能仅存在原验证机器；文档列出的路径不表示文件随源码或离线包交付。
