# SAE 部署基线

这个目录保存云上部署基线，覆盖以下几部分：

- 前端静态资源托管
- SAE 应用运行时
- MSE Nacos 配置中心与注册中心
- RDS / Redis / RabbitMQ 托管依赖
- OSS / CDN 文件分发

## 文件

- `service-matrix.md`：各服务的部署形态与健康检查矩阵
- `checklist.md`：发布前检查清单
- `runbook.md`：发布与回滚操作手册
- `env/sae-global.env.example`：SAE 环境变量基线
- `nacos/*.yaml.example`：共享配置与 7 个服务的 MSE Nacos 配置模板
