# S2 认证、设备会话与预算验收 — 2026-09-09

状态：S2 本地实现和隔离验收完成；未部署、未推送，不等于 S3-S7 完成。

## 已接通的链路

前端内存 access token + HttpOnly refresh Cookie → Gateway → auth-service → MySQL durable session。

- register-code / register / login / refresh / logout / forgot-password / reset-password 接入当前前端 payload。
- Cookie 名 semi_overt_refresh，Path=/api，HttpOnly，SameSite=Lax，Secure 默认 true；本地 HTTP 验收显式 false。
- rememberMe=false 不设置 Max-Age；true 持久 Cookie，刷新继续采用会话 persistent。
- refresh 使用随机32字节凭证，仅哈希落库；Access JWT最长900秒，包含 sid/sessionVersion。
- 登录读取密码时捕获sessionVersion，创建会话时锁用户并比较，避免密码重置后用旧认证结果创建新会话。
- auth-service权威校验签名、过期、sid、sessionVersion及设备状态，每次返回数据库当前角色；旧无sid JWT拒绝。网关不再New-Token续期，不再有独立JWT黑名单logout handler。
- 登出撤销当前设备；密码重置原子消费验证码、递增session_version、撤销该用户所有设备。
- 重放撤销在独立事务提交后才转401；验证码失败次数同样独立提交，不能因注册失败而回滚。
- 网关无效Bearer公开内容匿名降级；保护内容401。auth超时/故障503，不将网络故障当成永久退出。

## 验证码与预算

- 注册/重置6位验证码、随机salt、服务端pepper HMAC、10分钟有效、最多5次错误尝试、条件消费防重放。
- 邮件发送预算所有目的共用：邮箱60秒冷却/5小时/10天；IP 5分钟/30小时；全局500小时/2000天。
- auth-service为rate_limit_buckets唯一写入owner；网关经内部接口申请写操作/搜索/上传预算，无跨服务数据库写入。
- 多维预算同事务检查及扣减；拒绝不部分消耗；独立事务使业务失败不退款。沿用来源毫秒存储和滚动窗口，避免重置已迁移预算。
- 默认写120/60秒、搜索30/60秒（IP和已认证用户）；上传同时计入写预算及UTC日用户100次额度。返回429/Retry-After；预算依赖故障503。
- 清除公网伪造身份/IP/internal-token头；只有配置的trusted proxy才可影响XFF解析。网关重新注入可信IP和内部token，业务HeaderAuthenticationFilter必须验证内部token后才恢复用户身份。
- Origin精确白名单；拒绝跨站认证POST及通配来源。Turnstile旧placeholder绕过已移除，外部HTTP/SMTP超时有界。

## 测试与运行证据

最终 Maven verify（S1/S2隔离MySQL测试均开启）：123 tests / 0 failures / 0 errors / 0 skipped。
日志：D:\works\semi-overt-backend\.runtime\s2\s2-final-verify.log。

主要场景：
- MySQL并发刷新唯一轮换、重放撤销；事务失败回滚；重放撤销不随外层异常回滚。
- cookie persistence两分支、注册/重置验证码、5次失败后禁止正确码、重置后session_version变化、旧码不可重用、Origin拒绝、内部接口token拒绝。
- 多实例共享预算20并发仅5成功；多维拒绝无部分扣减；预算清理。
- HTTP auth authority client真实loopback调用、401与503区分；网关身份清洗、角色限制、cookie-only logout、预算429和Retry-After；trusted proxy/伪造XFF。
- 前端 npm run test:auth-session 14/14；npm run build通过。仅修正一条已过时的“不能有rememberMe/Checkbox”静态断言，使之匹配现有remember-me产品决策，未修改前端UI或运行代码。

## 真实浏览器验收

使用Edge隔离会话运行当前 D:\works\semi-overt 的实际 modules/auth.ts、authRuntime.ts、modules/user.ts；Vite临时代理到测试网关，不修改前端配置。

观察结果：
1. login 返回 user=browser01，当前用户接口返回id=1。
2. 页面reload后 refresh恢复browser01，access token存在。
3. logout后，旧Bearer请求users/me为401；Cookie refresh为401。
4. rememberMe=false登录和刷新后Cookie httpOnly=true、expires=-1、path=/api、sameSite=Lax；document.cookie无法读取refresh。

环境为真实Gateway RouteLocator/AuthFilter → auth-service Controller/Service → 隔离MySQL；SMTP/Turnstile mock，服务发现静态测试替身，旧Redis路由限流mock（新增MySQL预算为真实）。首页/公开文章等非S2服务未启动，页面会出现相关错误；不把这些页面或全站UI算通过。最初users/me测试因harness未导入UserController失败，修正测试装配后复测通过。

浏览器原始快照/console已移入忽略目录 D:\works\semi-overt-backend\.runtime\s2\playwright-artifacts；无凭证输出进Git。业务外部提供方连通、全服务和生产代理TLS将在S5/S6验收。

## 配置与部署前要求

- S1 V3 schema先到位；所有服务共享受保护 INTERNAL_TOKEN 配置，内网隔离不替代头验证。
- auth: JWT_SIGN_KEY（至少256位base64）、RESET_CODE_PEPPER、SMTP、Turnstile真实凭据必须由运维提供；未生成假生产凭据。
- gateway: INTERNAL_TOKEN、AUTH_SERVICE_BASE_URL默认http://auth-service（依赖注册发现）、TRUSTED_PROXIES默认空，不信任任意XFF。
- AUTH_ALLOWED_ORIGINS与网关CORS_ALLOWED_ORIGINS按实际前端origin一致设置；生产AUTH_REFRESH_COOKIE_SECURE=true。
- 升级后旧JWT不能继续使用，应按计划重新登录。新会话/验证码表存在不替代配置和schema检查。
- 新限流清理依赖auth的@EnableScheduling；现有路由Redis限流保留为附加入口保护。

## 明确未做

没有生产部署/数据库迁移/真实SMTP或Turnstile调用；没有S3文章CAS/审核状态机改造；没有S4存储搜索补齐；没有S5全站验收或S6容量/故障发布演练。不能直接替换线上旧后端。
