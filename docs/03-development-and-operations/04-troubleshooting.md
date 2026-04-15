# 排障手册

适合谁看：本地启动失败、联调异常、冒烟不过或部署后服务异常时。  
读完能解决什么问题：快速缩小问题范围，知道先查哪一层。

## 服务起不来

先看：

1. `.codex-runtime/logs`
2. `.codex-runtime/pids`
3. `dev-up.ps1` 终端输出

重点排查：

- 端口是否被占用
- Maven 是否可用
- `NACOS_NAMESPACE` 是否正确
- `PLATFORM_INTERNAL_TOKEN` 是否已通过环境变量或 Nacos 提供
- 中间件是否健康

## Docker 中间件异常

优先检查：

- `docker compose ps`
- `docker compose logs <service>`

已知本地问题之一：

- Redis 卷如果包含更新版本生成的数据，`dev-up.ps1` 会提示删除本地 Redis 容器与卷后重试

## `actuator/health` 不为 `UP`

通常先看：

- 目标服务日志
- 对应数据库 / Redis / RabbitMQ / Nacos 是否可连
- 环境变量和 Nacos 配置是否齐全

## 网关鉴权异常

现象与切入点：

- 需要登录的接口没有带 token 却能访问：先查 `gateway-service`
- 无效 token 不是 `401`：先查 `GatewayAuthFilter`
- 审核接口普通用户能访问：先查网关管理员限制和 `review-service` 权限配置

## 冒烟脚本失败

[scripts/smoke-test.ps1](../../scripts/smoke-test.ps1) 失败时，按顺序看：

1. Docker 依赖健康
2. 7 个业务服务端口和 `actuator`
3. 注册 / 登录是否成功
4. MySQL 中通知表与投递表是否入库
5. RabbitMQ 队列是否存在
6. 搜索结果是否最终可见

## Linux 启动脚本报错

常见原因：

- `java` 不在 `PATH`
- 没有先执行打包，导致 `target` 下没有可运行 jar
- `target` 下有多个可运行 jar
- `NACOS_SERVER_ADDR` 或 `NACOS_NAMESPACE` 缺失

## Nginx 看起来正常，但外部请求失败

先区分是哪一层失败：

- Nginx 自己生成 `500/502/503/504`：先看 Nginx 日志和 `gateway-service` 是否存活
- 业务返回 JSON 错误：再看网关和具体服务
- 图片打不开：先看 `/static/uploads/` 是否能通过网关访问，再看文件服务与存储目录
