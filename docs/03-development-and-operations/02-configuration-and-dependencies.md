# 配置来源与运行依赖

适合谁看：需要确认配置优先级、环境变量、Nacos 导入关系和服务依赖的人。  
读完能解决什么问题：知道当前运行时配置从哪里来，每个服务依赖哪些中间件，以及部署时必须准备什么。

## 配置来源顺序

当前运行基线依赖两类配置源：

1. 环境变量
2. Nacos 配置导入

各服务自身的 `application.yml` 主要承载：

- 服务名
- 默认端口
- Nacos 导入关系
- 本地默认值
- 管理端点暴露

## 共享配置导入

常见共享配置：

- `shared-common.yaml`
- `shared-db.yaml`
- `shared-redis.yaml`
- `shared-jwt.yaml`

服务专属配置通常按：

- `${spring.application.name}.yaml`
- `${spring.application.name}-${spring.profiles.active}.yaml`

从 Nacos 中导入。

## 关键环境变量

可直接参考 [scripts/env/server.env.example](../../scripts/env/server.env.example)。

重点变量：

- `SPRING_PROFILES_ACTIVE`
- `NACOS_SERVER_ADDR`
- `NACOS_NAMESPACE`
- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `REDIS_HOST` / `REDIS_PORT` / `REDIS_DB`
- `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`
- `JWT_SIGN_KEY` / `JWT_EXPIRATION` / `JWT_REMEMBER_ME_EXPIRATION` / `JWT_REFRESH_THRESHOLD`
- `MAIL_*`
- `FRONTEND_BASE_URL`
- `STORAGE_UPLOAD_PATH` / `STORAGE_ACCESS_PREFIX` / `STORAGE_MAX_FILE_SIZE`

## 服务依赖矩阵

- `gateway-service`：Redis、Nacos、JWT 配置
- `auth-service`：MySQL、Redis、Nacos、邮件配置、JWT 配置
- `content-service`：MySQL、Redis、RabbitMQ、Nacos
- `review-service`：MySQL、RabbitMQ、Nacos
- `search-service`：MySQL、RabbitMQ、Nacos
- `file-service`：Nacos、本地文件系统路径
- `notification-service`：MySQL、RabbitMQ、Nacos

## 管理端点

各服务当前都暴露：

- `/actuator/health`
- `/actuator/info`

这也是 `dev-up.ps1` 和 `smoke-test.ps1` 的核心就绪判断依据。

## 配置排查时先看什么

1. 目标服务 `src/main/resources/application.yml`
2. 环境变量是否正确注入
3. Nacos 地址与命名空间是否正确
4. 中间件端口与凭证是否匹配
5. 是否存在只在本地默认值下才成立的假设
