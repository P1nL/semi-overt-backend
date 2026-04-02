# SAE 发布前检查清单

- SAE 命名空间、MSE Nacos、RDS、Redis、RabbitMQ、OSS 与 CDN 位于同一地域和 VPC
- RDS / Redis / RabbitMQ 的白名单或安全组规则已允许 SAE 访问
- MSE 命名空间已创建，且所有必需配置集均已就绪
- `gateway-service` 是唯一对公网暴露的后端服务
- 前端静态资源域名与 API 域名已绑定，HTTPS 已准备完成
- `OSS_PUBLIC_BASE_URL` 可访问，并且指向目标 Bucket / CDN
- 已通过 `scripts/db-backup.ps1` 或 `scripts/db-backup.sh` 生成最新数据库备份
- Flyway 迁移已成功执行
- 所有 SAE 应用的就绪状态均为 `UP`
- 网关公网路由与无效 token 语义已验证通过
- 主链路冒烟通过：注册 -> 登录 -> 草稿 -> 提审 -> 审核通过 -> 搜索可见
- 上传接口返回的 OSS / CDN URL 可直接访问
- 前端静态资源、SAE 应用版本与数据库备份的回滚材料均已准备好
