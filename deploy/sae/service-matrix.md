# SAE 服务矩阵

| 服务 | 是否公网暴露 | 健康检查 | 就绪检查 Bean | 初始规格 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `gateway-service` | 是 | `/actuator/health/liveness`, `/actuator/health/readiness` | `nacosTcp` + reactive Redis | 1C2G | 唯一的公网后端入口 |
| `auth-service` | 否 | `/actuator/health/liveness`, `/actuator/health/readiness` | `authDependencies` | 1C2G | 依赖 MySQL + Redis |
| `content-service` | 否 | `/actuator/health/liveness`, `/actuator/health/readiness` | `contentDependencies` | 1C2G | 依赖 MySQL + Redis + RabbitMQ |
| `review-service` | 否 | `/actuator/health/liveness`, `/actuator/health/readiness` | `reviewDependencies` | 1C2G | 依赖 MySQL + RabbitMQ |
| `search-service` | 否 | `/actuator/health/liveness`, `/actuator/health/readiness` | `searchDependencies` | 1C2G | 依赖 MySQL + RabbitMQ |
| `file-service` | 否 | `/actuator/health/liveness`, `/actuator/health/readiness` | `fileStorage` | 1C2G | 使用 OSS 而不是本地磁盘 |
| `notification-service` | 否 | `/actuator/health/liveness`, `/actuator/health/readiness` | `notificationDependencies` | 1C2G | 依赖 MySQL + RabbitMQ |

## 启动

- 打包形态：Spring Boot 可执行 JAR
- 建议启动命令：`java -jar app.jar`
- 必需共享配置：`shared-common.yaml`、`shared-db.yaml`、`shared-redis.yaml`、`shared-jwt.yaml`
- 发布前必须准备的备份物料：由 `scripts/db-backup.ps1` 或 `scripts/db-backup.sh` 生成的数据库备份
- 发布顺序：
  1. `auth-service`
  2. `content-service`
  3. `review-service`
  4. `search-service`
  5. `notification-service`
  6. `file-service`
  7. `gateway-service`
