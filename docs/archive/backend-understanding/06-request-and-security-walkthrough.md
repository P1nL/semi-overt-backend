# 06 请求链路与安全逐文件讲解

## 为什么读这篇

这篇用来回答“一个公网请求是怎样被识别、鉴权、透传到下游服务的”。如果你后续要改权限、修 token 异常、增加内部调用或排查跨服务身份丢失，这篇是直接入口。

## 本篇覆盖哪些文件

- `gateway-service` 中的鉴权、JWT、路由、限流和异常处理文件
- 各服务自己的 `SecurityConfig`
- `common` 中的内部头、上下文和请求透传支持文件

## `GatewayAuthFilter`

文件位置：

- [../../gateway-service/src/main/java/com/platform/gateway/filter/GatewayAuthFilter.java](../../gateway-service/src/main/java/com/platform/gateway/filter/GatewayAuthFilter.java)

文件职责：

- 这是公网请求进入系统后的第一道认证过滤器
- 它负责识别哪些路由需要放行、哪些需要校验 token，并把身份信息写进内部头

关键行为：

- 读取请求头中的 token
- 调用 `GatewayJwtHelper` 解析和校验
- 对公开路由放行
- 对受保护路由注入 `X-User-Id`、`X-Username`、`X-User-Role`、`X-Trace-Id`
- 无效 token 直接返回 `401`

依赖关系：

- 依赖 `GatewayJwtHelper`
- 依赖 [HeaderNames.java](../../common/src/main/java/com/platform/common/constant/HeaderNames.java) 提供内部头常量
- 受网关路由和异常处理配置配合

修改风险：

- 一旦这里误放行或误拦截，请求权限语义会全线跑偏
- 一旦内部头写错，所有下游服务都可能拿不到用户上下文

常见改动入口：

- 新增公开路由
- 调整 token 缺失或非法时的响应策略
- 增加内部头字段

## `GatewayJwtHelper`

文件位置：

- [../../gateway-service/src/main/java/com/platform/gateway/security/GatewayJwtHelper.java](../../gateway-service/src/main/java/com/platform/gateway/security/GatewayJwtHelper.java)

文件职责：

- 封装网关侧 JWT 的解析与校验逻辑

关键行为：

- 根据共享签名密钥校验 token
- 提取用户 ID、用户名、角色等声明

依赖关系：

- 被 `GatewayAuthFilter` 直接调用
- 配置来源于网关 `application.yml` 与 Nacos 的 JWT 相关配置

修改风险：

- 这里与 `auth-service` 里的 JWT 生成规则必须保持一致
- 只改一边会导致“能登录但网关认不出 token”

常见改动入口：

- JWT claim 结构变化
- 签名密钥或过期时间策略变化

## `GatewayRouteConfig`

文件位置：

- [../../gateway-service/src/main/java/com/platform/gateway/config/GatewayRouteConfig.java](../../gateway-service/src/main/java/com/platform/gateway/config/GatewayRouteConfig.java)

文件职责：

- 定义网关把哪些外部路径转发到哪个服务

关键行为：

- 将认证、用户、内容、审核、搜索、上传相关路径路由到对应服务
- 为这些路由统一挂载限流过滤器

依赖关系：

- 依赖 Spring Cloud Gateway
- 与 `GatewayAuthFilter` 一起构成公网入口治理

修改风险：

- 路由改错会直接导致接口 404 或打到错误服务
- 如果新增服务或路径忘记在这里接入，前端永远进不去

常见改动入口：

- 新增网关公开路径
- 拆分或合并业务模块路径

## `GatewayRateLimitConfig`

文件位置：

- [../../gateway-service/src/main/java/com/platform/gateway/config/GatewayRateLimitConfig.java](../../gateway-service/src/main/java/com/platform/gateway/config/GatewayRateLimitConfig.java)

文件职责：

- 提供默认限流器和限流 key 解析方式

关键行为：

- 结合 Redis 做请求频率限制
- 决定按什么粒度识别客户端

依赖关系：

- 被 `GatewayRouteConfig` 使用
- 依赖 Redis

修改风险：

- key 规则变动会改变限流统计维度
- 配额设置过低会误伤正常请求，过高则没有保护价值

常见改动入口：

- 调整全局限流参数
- 换限流 key 维度

## `GatewayJsonExceptionHandler`

文件位置：

- [../../gateway-service/src/main/java/com/platform/gateway/config/GatewayJsonExceptionHandler.java](../../gateway-service/src/main/java/com/platform/gateway/config/GatewayJsonExceptionHandler.java)

文件职责：

- 把网关层异常统一转换成 JSON 响应

关键行为：

- 避免网关在异常时返回不可用的默认 HTML
- 让前端拿到一致的错误结构

依赖关系：

- 与 `GatewayAuthFilter`、路由和限流配置共同组成入口异常面

修改风险：

- 这里如果吞掉错误上下文，排障会变得困难

常见改动入口：

- 统一错误码格式时

## `GatewayCorsConfig`

文件位置：

- [../../gateway-service/src/main/java/com/platform/gateway/config/GatewayCorsConfig.java](../../gateway-service/src/main/java/com/platform/gateway/config/GatewayCorsConfig.java)

文件职责：

- 统一定义网关跨域规则

关键行为：

- 控制前端域名能否跨域访问网关

依赖关系：

- 作用于所有经网关暴露的公网 API

修改风险：

- 放太宽会增加风险
- 写错正式域名会导致前端线上直接被浏览器拦截

常见改动入口：

- 切换前端域名
- 新增环境域名白名单

## 各服务 `SecurityConfig`

文件位置：

- [../../auth-service/src/main/java/com/platform/config/SecurityConfig.java](../../auth-service/src/main/java/com/platform/config/SecurityConfig.java)
- [../../content-service/src/main/java/com/platform/config/SecurityConfig.java](../../content-service/src/main/java/com/platform/config/SecurityConfig.java)
- [../../review-service/src/main/java/com/platform/config/SecurityConfig.java](../../review-service/src/main/java/com/platform/config/SecurityConfig.java)
- [../../search-service/src/main/java/com/platform/config/SecurityConfig.java](../../search-service/src/main/java/com/platform/config/SecurityConfig.java)
- [../../file-service/src/main/java/com/platform/config/SecurityConfig.java](../../file-service/src/main/java/com/platform/config/SecurityConfig.java)
- [../../notification-service/src/main/java/com/platform/config/SecurityConfig.java](../../notification-service/src/main/java/com/platform/config/SecurityConfig.java)

文件职责：

- 定义每个服务自己的访问边界
- 决定哪些路径需要身份、哪些内部路径可放行、哪些角色可进入

关键行为：

- 配合 `HeaderAuthenticationFilter` 把网关注入的内部头转成 Spring Security 上下文
- 收敛公开、登录、普通用户、管理员和内部接口的边界

依赖关系：

- 依赖 `common` 中的安全过滤和上下文支持类
- 与网关入口形成“两层防线”

修改风险：

- 网关放行不代表服务侧一定放行
- 服务侧配置太宽会让内部接口暴露出错

常见改动入口：

- 新增管理员接口
- 调整内部接口放行规则

## `HeaderNames`

文件位置：

- [../../common/src/main/java/com/platform/common/constant/HeaderNames.java](../../common/src/main/java/com/platform/common/constant/HeaderNames.java)

文件职责：

- 集中定义内部头协议常量

关键行为：

- 统一 `X-User-Id`、`X-Username`、`X-User-Role`、`X-Trace-Id`

依赖关系：

- 网关写入
- 各服务读取
- Feign 透传继续使用

修改风险：

- 改一个字符串就是全链路协议变化

常见改动入口：

- 新增跨服务上下文字段

## `HeaderAuthenticationFilter`

文件位置：

- [../../common/src/main/java/com/platform/common/security/HeaderAuthenticationFilter.java](../../common/src/main/java/com/platform/common/security/HeaderAuthenticationFilter.java)

文件职责：

- 让下游服务把内部头转成服务内可用的认证上下文

关键行为：

- 从内部头读取用户信息
- 写入 Spring Security 认证对象和上下文

依赖关系：

- 被各服务的 `SecurityConfig` 装配
- 依赖 `HeaderNames`

修改风险：

- 这里一旦解析规则和网关不一致，下游服务会出现“请求已登录但拿不到用户”的假象

常见改动入口：

- 内部头协议扩展

## `FeignHeaderRelayInterceptor`

文件位置：

- [../../common/src/main/java/com/platform/common/feign/FeignHeaderRelayInterceptor.java](../../common/src/main/java/com/platform/common/feign/FeignHeaderRelayInterceptor.java)

文件职责：

- 在服务间 HTTP 调用时继续透传用户头和 TraceId

关键行为：

- 把当前线程上下文里的头信息带到 Feign 请求里

依赖关系：

- 依赖 `TraceContextHolder` 和 `UserContextHolder`
- 被跨服务调用链使用

修改风险：

- 如果透传不完整，内部接口虽然能调通，但会丢失调用身份和链路追踪

常见改动入口：

- 新增需要透传的内部头

## `TraceContextHolder` 与 `UserContextHolder`

文件位置：

- [../../common/src/main/java/com/platform/common/context/TraceContextHolder.java](../../common/src/main/java/com/platform/common/context/TraceContextHolder.java)
- [../../common/src/main/java/com/platform/common/context/UserContextHolder.java](../../common/src/main/java/com/platform/common/context/UserContextHolder.java)

文件职责：

- 保存当前请求线程内的追踪信息和用户信息

关键行为：

- 供过滤器、Feign 透传和业务代码读取当前上下文

依赖关系：

- 上游由网关和 Header 过滤器写入
- 下游由 Feign 拦截器和业务服务读取

修改风险：

- 线程上下文如果清理不当，可能导致串请求污染

常见改动入口：

- 增加上下文字段

## 为什么网关是公网统一鉴权入口

- 公网 token 在最外层解析，权限语义最容易统一
- 下游服务只认内部头和本地安全配置，逻辑更清晰
- 一旦需要修复无效 token、限流或 TraceId 问题，集中在网关更容易收敛

## 读完这篇后你应该知道什么

- 公网认证起点在 `GatewayAuthFilter`
- JWT 规则必须在网关和认证服务之间保持一致
- 服务侧 `SecurityConfig` 负责第二层边界，不是多余配置
- 内部头和 TraceId 是跨服务链路能否对齐的基础协议
