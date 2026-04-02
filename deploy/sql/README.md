# SQL 策略

适合谁看：准备数据库初始化、升级或回滚资产的开发和运维同学。  
读完能解决什么问题：知道什么时候用 `init.sql`，什么时候只允许走 Flyway，发布前该准备什么备份。

## 结论

- `deploy/sql/init.sql` 只用于新环境一次性初始化。
- `db/migration/V*.sql` 是唯一允许进入生产升级链路的数据库变更入口。
- 发布前必须先做数据库备份，再执行 Flyway，再部署 SAE 应用。
- 回滚不做自动 down migration；数据库问题按“恢复备份”或“前滚修复”处理。

## 文件职责

- `deploy/sql/init.sql`
  - 同时包含建库、建表和本地/演示种子数据。
  - 适合本地开发、联调环境、新演示环境。
  - 不适合已存在数据的线上升级。
- `db/migration/V*.sql`
  - 只放版本化 schema 变更。
  - 命名固定为 `V<版本号>__<说明>.sql`。
  - 由 `db-migration` 模块和 `scripts/flyway-migrate.*` 执行。

## 发布顺序

1. 备份目标数据库
   - Windows: `.\scripts\db-backup.ps1`
   - Linux: `./scripts/db-backup.sh`
2. 执行 Flyway
   - Windows: `.\scripts\flyway-migrate.ps1`
   - Linux: `./scripts/flyway-migrate.sh`
3. 核对 `flyway_schema_history`
4. 再发布 SAE 应用

## 变更要求

- 每个新的 `V*.sql` 必须是幂等可审查的。
- DDL 需要在 MR/PR 说明中写明：
  - 是否不可逆
  - 执行前备份要求
  - 失败后的恢复路径
- 演示数据或本地假数据不进入 `db/migration`，继续留在 `init.sql` 或单独的 demo SQL 中。
