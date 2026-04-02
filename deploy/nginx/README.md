# Nginx 外部入口基线

本目录在现有 Spring Boot 运行基线之上，再补一层面向 Linux 主机的轻量 Nginx 外部入口。

它不会替代下面这些现有能力：

- `scripts/run-service.sh <service-name> [profile] [env-file]`
- 直接访问 `8080-8086` 的服务健康检查
- 当前由网关负责的鉴权、TraceId 生成、限流和服务路由

## 职责

Nginx 是唯一的公网 HTTP 入口：

`Nginx -> gateway-service -> internal services`

对外暴露的契约如下：

- `http://<host>/`
- `http://<host>/api/v1/**`
- `http://<host>/static/uploads/**`

Nginx 不会直接代理到 `auth-service`、`content-service`、`review-service`、`search-service`、`file-service` 或 `notification-service`。

## 路径

生产环境 Linux 路径：

- 静态资源根目录：`/srv/now-demo/frontend/dist`
- 配置目标路径：`/etc/nginx/conf.d/now-demo.conf`
- 访问日志：`/var/log/nginx/now-demo.access.log`
- 错误日志：`/var/log/nginx/now-demo.error.log`

本机前端仓库示例路径：

- `C:\Users\PINKING\WebstormProjects\now`

这个路径只作为本地构建示例。服务器侧基线仍然假设使用 Linux 路径。

## 前端构建

本机构建示例：

```powershell
Set-Location 'C:\Users\PINKING\WebstormProjects\now'
npm run build
```

把生成后的 `dist/` 内容复制到：

```text
/srv/now-demo/frontend/dist
```

前端 API 基地址已经默认是 `/api/v1`，因此 Nginx 不需要额外做路径重写。

## 部署

1. 先启动中间件和 Spring 服务。
2. 将 `deploy/nginx/now-demo.conf` 复制到 `/etc/nginx/conf.d/now-demo.conf`。
3. 确认 `/srv/now-demo/frontend/dist` 中已经放入打包后的前端静态资源。
4. 校验 Nginx 配置：

```bash
nginx -t
```

5. 重新加载 Nginx：

```bash
systemctl reload nginx
```

如果宿主机没有使用 `systemd`，则执行：

```bash
nginx -s reload
```

## 行为说明

- `/api/` 代理到 `http://127.0.0.1:8080`。
- `/static/uploads/` 也代理到 `http://127.0.0.1:8080`。
- `/actuator/health` 仅用于本机探测，对非本机客户端拒绝访问。
- SPA 刷新会回退到 `index.html`。
- `/assets/` 使用长期不可变缓存。
- `index.html` 不使用长期缓存。
- `/api/` 不会再次包装网关返回的 JSON。
- 如果 `/api/` 发生 `500/502/503/504` 且需要由 Nginx 自己生成响应，会返回 JSON，而不是默认 HTML 错误页。
- `/static/uploads/` 保持普通静态资源语义；如果网关不可用，图片加载失败属于预期现象。

## 重要运维提醒

这层 Nginx 会新增公网 `80` 端口，但它本身不会自动隐藏 `8080-8086`。
仍然需要通过主机防火墙、安全组或上游网络策略，把各服务端口对公网关闭。

`scripts/smoke-test.ps1` 不需要改动，仍然直接访问 `http://127.0.0.1:8080` 上的网关。
