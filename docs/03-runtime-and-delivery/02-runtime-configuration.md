# 02 运行配置说明

## 为什么读这份文档

这份文档解决的是“配置到底从哪里来、为什么本地和服务器行为会不一样、哪些配置必须跨服务一致”的问题。它适合在排配置覆盖、准备上线、或者新增环境变量时阅读。

## 当前配置优先级

当前配置优先级固定为：

1. 环境变量
2. Nacos 配置
3. 各模块本地 `application.yml`

这条规则的含义：

- 本地 `application.yml` 是默认值，不是最终事实
- Nacos 是跨环境共享配置和服务级配置中心
- 环境变量用于覆盖目标环境下最关键的运行参数

## Nacos 负责什么

Nacos 同时承担：

- 配置中心
- 服务发现

当前约定：

- `group`：`NOW_DEMO`
- 共享 dataId：
  - `shared-common.yaml`
  - `shared-db.yaml`
  - `shared-redis.yaml`
  - `shared-jwt.yaml`
- 服务级 dataId：
  - `<service>.yaml`
  - `<service>-<profile>.yaml`

## 环境变量负责什么

环境变量负责在目标环境中给出最直接、最显式的运行参数覆盖。

常见变量：

- `SPRING_PROFILES_ACTIVE`
- `NACOS_SERVER_ADDR`
- `NACOS_NAMESPACE`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `ELASTICSEARCH_URIS`
- `JWT_SIGN_KEY`
- `STORAGE_UPLOAD_PATH`
- `STORAGE_ACCESS_PREFIX`

## 哪些配置必须跨服务一致

### JWT 相关配置

网关和认证服务必须使用同一套签名密钥与过期策略认知。

### 中间件连接配置

至少在共享同一套基础设施的服务之间保持一致：

- MySQL
- Redis
- RabbitMQ
- Elasticsearch
- Nacos

### 内部协议相关配置

虽然它们不是传统环境变量，但也属于跨服务一致性约束：

- 内部头协议
- 服务名
- 路由路径

## 文件存储相关配置

当前 `file-service` 仍采用本地文件系统基线。

关键配置：

- `STORAGE_UPLOAD_PATH`
- `STORAGE_ACCESS_PREFIX`
- `STORAGE_MAX_FILE_SIZE`

如果这些配置和网关或 Nginx 的静态资源路径理解不一致，最终表现会是“上传成功了，但图片访问不到”。
