# Nginx External Entry Baseline

This folder adds a lightweight Linux-facing Nginx layer on top of the existing Spring Boot runtime baseline.

It does not replace:

- `scripts/run-service.sh <service-name> [profile] [env-file]`
- direct service health checks on `8080-8086`
- the current gateway responsibility for auth, TraceId generation, rate limiting, and service routing

## Responsibilities

Nginx is the only public HTTP entry:

`Nginx -> gateway-service -> internal services`

External contract:

- `http://<host>/`
- `http://<host>/api/v1/**`
- `http://<host>/static/uploads/**`

Nginx does not proxy directly to `auth-service`, `content-service`, `review-service`, `search-service`, `file-service`, or `notification-service`.

## Paths

Production Linux paths:

- static root: `/srv/now-demo/frontend/dist`
- config target: `/etc/nginx/conf.d/now-demo.conf`
- access log: `/var/log/nginx/now-demo.access.log`
- error log: `/var/log/nginx/now-demo.error.log`

Local example frontend repo on this machine:

- `C:\Users\PINKING\WebstormProjects\now`

Use that path only as a build source example. The server baseline still assumes Linux paths.

## Frontend Build

Example local build:

```powershell
Set-Location 'C:\Users\PINKING\WebstormProjects\now'
npm run build
```

Copy the generated `dist/` contents to:

```text
/srv/now-demo/frontend/dist
```

The frontend API base already defaults to `/api/v1`, so no Nginx path rewriting is required.

## Deploy

1. Start middleware and Spring services first.
2. Copy `deploy/nginx/now-demo.conf` to `/etc/nginx/conf.d/now-demo.conf`.
3. Ensure `/srv/now-demo/frontend/dist` contains the built frontend assets.
4. Validate the Nginx config:

```bash
nginx -t
```

5. Reload Nginx:

```bash
systemctl reload nginx
```

If your host does not use `systemd`, use:

```bash
nginx -s reload
```

## Behavior Notes

- `/api/` proxies to `http://127.0.0.1:8080`.
- `/static/uploads/` also proxies to `http://127.0.0.1:8080`.
- `/actuator/health` is only intended for local host probing and is denied to non-local clients.
- SPA refreshes fall back to `index.html`.
- `/assets/` uses long immutable caching.
- `index.html` is not long-cached.
- `/api/` does not wrap gateway JSON responses.
- If Nginx itself has to generate a `500/502/503/504` for `/api/`, it returns JSON instead of the default HTML error page.
- `/static/uploads/` keeps normal static-resource semantics. If the gateway is down, broken image loading is expected.

## Important Ops Reminder

This Nginx layer adds public port `80`, but it does not by itself hide `8080-8086`.
Keep the service ports closed to the public network through host firewall rules, security groups, or upstream network policy.

`scripts/smoke-test.ps1` stays unchanged and still targets gateway directly on `http://127.0.0.1:8080`.
