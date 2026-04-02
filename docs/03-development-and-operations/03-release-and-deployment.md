# 发布与部署基线

适合谁看：准备把服务部署到 Linux 主机或补齐外部入口的人。  
读完能解决什么问题：知道当前仓库默认发布方式是什么、Nginx 在链路中的位置以及部署顺序。

## 当前正式发布基线

当前仓库默认不是把业务服务整体容器化运行，而是在 Linux 主机上直接以 `java -jar` 启动各服务。

入口脚本：

- [scripts/run-service.sh](../../scripts/run-service.sh)

## `run-service.sh` 的使用方式

```bash
scripts/run-service.sh <service-name> [profile] [env-file]
```

示例：

```bash
cp ./scripts/env/server.env.example ./scripts/env/server.env
./scripts/run-service.sh gateway-service server ./scripts/env/server.env
```

脚本行为：

- 要求目标模块目录存在
- 可选加载一个环境变量文件
- 要求 `java` 在 `PATH`
- 强制要求 `NACOS_SERVER_ADDR` 与 `NACOS_NAMESPACE`
- 从 `<service>/target` 中找到唯一可运行 jar
- 把日志写入 `.runtime/logs`
- 把 PID 写入 `.runtime/pids`

## 推荐发布顺序

1. 准备环境变量和 Nacos 配置
2. 确保 MySQL、Redis、RabbitMQ、Nacos 可用
3. 打包服务 jar
4. 先启动下游真源和支撑服务，再启动网关
5. 用 `actuator` 和业务冒烟验证
6. 如需外部入口，再挂 Nginx

## Nginx 的角色

当前可选 Nginx 基线位于：

- [deploy/nginx/README.md](../../deploy/nginx/README.md)
- [deploy/nginx/now-demo.conf](../../deploy/nginx/now-demo.conf)

云上 SAE / MSE / OSS 基线位于：

- [deploy/sae/README.md](../../deploy/sae/README.md)
- [deploy/sae/service-matrix.md](../../deploy/sae/service-matrix.md)
- [deploy/sae/runbook.md](../../deploy/sae/runbook.md)
- [deploy/sql/README.md](../../deploy/sql/README.md)

链路是：

`Nginx -> gateway-service -> internal services`

Nginx 负责：

- 暴露公网 `80`
- 承载前端静态资源
- `/api/` 与 `/static/uploads/` 反向代理到 `gateway-service`
- 对自身生成的 `500/502/503/504` 返回 JSON

Nginx 不负责：

- 直接代理到各业务服务
- 替代网关的鉴权、限流、路由职责

## 回滚思路

当前回滚策略仍是传统主机部署思路：

- 保留上一版可运行 jar
- 通过脚本停止当前进程并恢复上一版
- 回滚后优先检查 `actuator`、关键接口和主链路冒烟

如果带了 Nginx：

- 业务回滚与 Nginx 配置回滚应拆开处理
- Nginx 只在入口或静态资源策略变更时需要一起回滚
