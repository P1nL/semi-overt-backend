# SAE 发布运行手册

## 1. 准备阶段

- 确认云资源和网络白名单已经就绪
- 上传前端静态资源到托管平台
- 更新 MSE Nacos 的共享配置与服务配置
- 核对 7 个服务在 SAE 上的环境变量
- 在把值同步到 MSE 前，先填写对应的 `deploy/sae/nacos/*-prod.yaml.example` 文件

## 2. 数据库

- 备份目标数据库
  - Windows: `.\scripts\db-backup.ps1`
  - Linux: `./scripts/db-backup.sh`
- 执行 Flyway 迁移
  - Windows: `.\scripts\flyway-migrate.ps1`
  - Linux: `./scripts/flyway-migrate.sh`
- 确认 schema history 表已更新
- 不要对已有生产数据库执行 `deploy/sql/init.sql`

## 3. 部署

- 先部署非公网服务
- 最后部署 `gateway-service`
- 等待所有服务的就绪状态都返回 `UP`
- 在冒烟通过前，保留上一版 SAE 发布物和本次生成的数据库备份

## 4. 验证

- 验证 `/actuator/health/liveness`
- 验证 `/actuator/health/readiness`
- 通过 `gateway-service` 验证公网 API
- 验证上传接口会返回 OSS / CDN URL
- 运行业务冒烟链路
  - `.\scripts\smoke-test-sae.ps1 -GatewayBaseUrl <api-domain> -AdminAccount <admin> -AdminPassword <password>`

## 5. 回滚

- 应用问题：把受影响的 SAE 应用回滚到上一版
- 前端问题：只回滚静态资源托管物料
- 数据库问题：结合备份和迁移记录执行恢复或前滚修复
- 如果迁移不可逆，优先选择前滚修复，并把失败版本隔离在公网流量之外
