# 脚本与入口文件说明

适合谁看：需要知道仓库里哪些脚本是正式入口、哪些文件是运行基线的人。  
读完能解决什么问题：知道启动、停止、冒烟、部署和架构校验分别该用什么。

## 本地开发脚本

### [scripts/dev-up.ps1](../../scripts/dev-up.ps1)

用途：

- 启动 Docker 中间件
- 准备 Maven 本地仓库与运行时目录
- 启动业务服务并等待就绪

### [scripts/dev-down.ps1](../../scripts/dev-down.ps1)

用途：

- 停止本地开发运行的服务

### [scripts/dev-up.cmd](../../scripts/dev-up.cmd) / [scripts/dev-down.cmd](../../scripts/dev-down.cmd)

用途：

- Windows 命令行包装入口

## 验证脚本

### [scripts/smoke-test.ps1](../../scripts/smoke-test.ps1)

用途：

- 健康检查
- 网关语义检查
- 端到端业务主链路冒烟

额外能力：

- 可带 `-SkipE2E` 只跑基础健康检查
- 会直连 MySQL、RabbitMQ 管理台、网关接口

## Linux 启动脚本

### [scripts/run-service.sh](../../scripts/run-service.sh)

用途：

- 在 Linux 主机上用 `java -jar` 启动单个 Spring Boot 服务

依赖前提：

- 已打包出唯一可运行 jar
- 已准备环境变量
- `java` 可用

## 环境变量样例

### [scripts/env/server.env.example](../../scripts/env/server.env.example)

用途：

- 提供服务器基线环境变量样例
- 覆盖数据库、Redis、RabbitMQ、JWT、邮件、文件存储等关键配置

## 入口配置与基础设施文件

### [docker-compose.yml](../../docker-compose.yml)

本地中间件栈定义。

### [deploy/sql/init.sql](../../deploy/sql/init.sql)

本地 MySQL 初始化脚本。

### [deploy/nginx/README.md](../../deploy/nginx/README.md)

Nginx 外部入口基线说明。

### [deploy/nginx/now-demo.conf](../../deploy/nginx/now-demo.conf)

Nginx 示例配置。

## 架构校验入口

### [architecture-tests/src/test/java/com/platform/architecture/FinalArchitectureTest.java](../../architecture-tests/src/test/java/com/platform/architecture/FinalArchitectureTest.java)

用途：

- 约束模块集
- 约束 Feign 位置
- 约束事件基础设施归属
- 约束服务入口边界
