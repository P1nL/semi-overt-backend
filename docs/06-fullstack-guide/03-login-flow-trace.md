# semi-overt 登录与设备会话全链路

> semi-overt · 文档整理 2026-09-15 · 现行说明：按源码与仓库配置整理；本次未重新执行运行时验收。 [文档中心](../README.md)

## 先纠正旧模型

当前不再采用“网关根据 JWT/黑名单独立认证，再通过 New-Token 响应头无感续期”的说明。登录、刷新、退出由 auth-service 承担；access token 与 refresh Cookie 各司其职。

## 1. 登录

前端向 POST /api/v1/auth/login 提交凭据。网关把认证入口路由至 AuthController，不要求旧 access token 已有效。AuthService 的会话登录流程验证用户并由 DeviceSessionService 管理持久化设备会话；响应返回 access token，同时通过 Set-Cookie 设置 refresh Cookie。

refresh Cookie 默认名称为 semi_overt_refresh，由后端控制。Cookie 的 Secure、来源白名单及前端 withCredentials 必须与实际 HTTP/HTTPS 和跨域环境匹配，不能把演示来源配置直接用于公网。

## 2. 访问受保护资源

演示前端 src/shared/api/authRuntime.ts 管理 access token 与刷新流程，http.ts 注入 Bearer。GatewayAuthFilter 清理外部伪造身份头，调用 SessionAuthorityClient；Auth 从持久化会话判断有效性，网关再转发可信用户 ID/名称/角色。

用户身份不是仅从客户端提供的 X-User-* 头或前端缓存中获取。用户信息的展示缓存也不是服务器授权依据。

## 3. 刷新与错误

POST /api/v1/auth/refresh 使用 refresh Cookie，成功后轮换并返回新的 access token。前端在受控范围合并刷新请求并避免递归刷新；认证接口有排除规则。

- 明确未授权与网络/503 必须区分；AuthController 仅在对应认证失效分支清理 Cookie。
- 不能因为暂时网络错误就永久清除可恢复会话。
- 不能让登录、刷新或有副作用写请求进入无限重试。
- 多标签页、并发刷新与撤销行为需要真实浏览器/数据库验证，不以单个接口 200 代替。

## 4. 退出

POST /api/v1/auth/logout 使用 Cookie 找到并撤销会话，清理 refresh Cookie。网关仅路由，不再负责旧 token 黑名单式退出。服务端撤销后旧会话应被 Auth 权威校验拒绝，而非只隐藏前端页面。

## 代码阅读顺序

1. 演示前端 src/shared/api/authRuntime.ts、http.ts。
2. [AuthController](../../auth-service/src/main/java/com/platform/auth/controller/AuthController.java)。
3. [DeviceSessionService](../../auth-service/src/main/java/com/platform/auth/session/DeviceSessionService.java)。
4. [JdbcDeviceSessionRepository](../../auth-service/src/main/java/com/platform/auth/repository/JdbcDeviceSessionRepository.java)。
5. [GatewayAuthFilter](../../gateway-service/src/main/java/com/platform/gateway/filter/GatewayAuthFilter.java)、[SessionAuthorityClient](../../gateway-service/src/main/java/com/platform/gateway/session/SessionAuthorityClient.java)。

历史：[S2 契约](../sync/s2-session-service.md)、[S5 恢复专项回执](../sync/s5-recovery-acceptance.md)。这些记录没有在本次文档更新中重新执行。旧版逐行登录讲解见[归档](../archive/fullstack-before-2026-09-15/README.md)，不要复制其中旧 localStorage/New-Token 实现。
