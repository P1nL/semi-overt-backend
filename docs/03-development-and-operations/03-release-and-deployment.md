# 发布与部署基线

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 当前仓库提供的交付方式

- 全 Docker 演示与离线包：[deploy/docker/README.md](../../deploy/docker/README.md)，由 scripts/docker-demo.ps1 管理 init/build/pull/up/down/status/logs/export/import。
- S5 是本机隔离联调环境，不是生产发布入口。
- scripts/run-service.sh、deploy/nginx、deploy/sae 是保留的主机/云配置参考；文件存在不证明已部署或已生产验收。

## 交付检查顺序

1. 记录后端和演示前端源码版本、未提交改动、构建命令、镜像 tag/digest；可变 demo 标签不等于不可变发布。
2. 检查 Compose 解析，保护目标机原有 .runtime 配置；确认端口与容器归属。
3. 备份数据库和上传文件，确认 Flyway 迁移与现有 schema 兼容。迁移成功退出与业务服务就绪分别检查。
4. 检查七个服务 readiness、前端/网关访问、静态资源 HTTP 状态与 MIME，再验证业务链路。
5. 单独记录模型、邮件等外部服务验证边界；离线镜像包不包含这些外部服务能力。
6. 发布证据应对应实际交付的镜像，不能把后来重建的镜像套用旧回执。

## 数据、配置与回滚

- 镜像包不是数据库或上传文件备份；目标机凭据、数据卷和上传目录需独立保管。
- 修改容器环境后重新创建对应容器，不能只 restart。RESET_CODE_PEPPER 和内部令牌必须一致且非空。
- 禁止用删卷、清库或覆盖目标机私有配置作为普通更新步骤。
- 回滚程序前先确认 schema 向后兼容性；应用回滚不会自动撤销迁移。保留上一版镜像及受保护的数据备份。

## 入口代理

公网请求应通过前端代理/网关，不直接暴露内部业务端口。AI 润色具有独立超时；上传静态路径要检查真实图片返回，不能落入前端 SPA 的 HTML fallback。

保留的 [Nginx 配置](../../deploy/nginx/now-demo.conf) 文件名是历史技术标识，不再用作项目展示名。对外开放前还需核对来源白名单、Cookie Secure、HTTPS、存储与访问控制；本地演示配置不是生产安全配置。
