# 01 本地开发与联调

## 为什么读这份文档

这份文档解决的是“在这台机器上到底该怎么把系统跑起来”和“为什么本地中间件与业务服务要分开管理”的问题。它面向第一次接手仓库、需要尽快完成本地联调或演示的后端同学。

## 当前本地运行模型

本地运行模型分成两层：

1. 中间件层
2. 业务服务层

对应入口分别是：

- 中间件层： [docker-compose.yml](../../docker-compose.yml)
- 业务服务层： [dev-up.ps1](../../scripts/dev-up.ps1)

### 为什么要分两层

因为中间件和业务服务的生命周期不同：

- MySQL、Redis、RabbitMQ、ES、Nacos 往往不需要每次改代码都重启
- Java 服务在日常开发中会频繁改、频繁重启

把两层拆开管理的好处：

- 本地联调更快
- 出问题时更容易判断是中间件没起来，还是服务本身没起来
- 脚本职责更清晰

## `docker-compose.yml` 负责什么

[docker-compose.yml](../../docker-compose.yml) 当前只负责本地中间件，不负责业务服务。

默认启动：

- MySQL `3306`
- Redis `6379`
- Nacos `8848`
- RabbitMQ `5672` / `15672`
- Elasticsearch `9200`
- 可选 Kibana `5601`

它的工作含义：

- 提供统一的本地依赖环境
- 保证不同同学的中间件形态尽量一致

## `dev-up.ps1` 负责什么

[dev-up.ps1](../../scripts/dev-up.ps1) 是当前 Windows 本地开发的主启动脚本。

它负责：

- 等待中间件健康
- 安装父 POM 和 `common`
- 按固定顺序启动服务
- 逐个检查端口、`/actuator/health`、`/actuator/info`
- 管理 `.codex-runtime` 下的日志和 PID

默认启动服务：

1. `auth-service`
2. `content-service`
3. `review-service`
4. `search-service`
5. `notification-service`
6. `file-service`
7. `gateway-service`

## `smoke-test.ps1` 负责什么

[smoke-test.ps1](../../scripts/smoke-test.ps1) 是当前本地联调和交付验收的最小事实检查脚本。

它会验证：

- Docker 中间件健康
- 所有服务的 actuator 接口
- 网关公开路由
- 无效 token 的 `401`
- 端到端业务链路：
  `注册 -> 登录 -> 创建文章 -> 提交审核 -> 审核通过 -> 通知落库 -> 搜索可见`

## `run-service.sh` 负责什么

[run-service.sh](../../scripts/run-service.sh) 是 Linux 主机基线的服务启动脚本。

它负责：

- 读取打包后的 Jar
- 读取 profile 和 env 文件
- 启动单个服务
- 把 PID 和日志写到 `.runtime`

它的工作含义是明确：当前正式发布基线是 `java -jar`，不是本地 `spring-boot:run`。

## 推荐的本地联调顺序

### 第一步：起中间件

```powershell
docker compose up -d
```

### 第二步：起业务服务

```powershell
.\scripts\dev-up.ps1
```

### 第三步：跑烟雾验证

```powershell
.\scripts\smoke-test.ps1
```

## 常见本地误区

- 只起服务，不起中间件
- 直接把 Linux 启动脚本当本地启动脚本
- 只看 PID，不看健康检查
