# 登录功能全链路追踪 —— 从前端点击到数据库查询的完整旅程

## 为什么选择登录功能作为示例

登录是整个系统里最值得解剖的功能。它看起来简单，实际上经过了前端路由守卫、表单验证、HTTP 拦截器、网关鉴权、Controller 参数校验、Service 业务逻辑、数据库查询、响应解包、状态管理、路由跳转这十几个环节。只要你能把登录流程看明白，项目里其他功能的套路你基本都掌握了。

本文按数据流动的先后顺序逐步拆解，每一步都会告诉你看哪个文件、关键代码是什么、这一步在整条链路里的作用是什么。

---

## 第一步：前端触发 —— 用户操作后发生了什么

### 路由定义

**文件:** `前端/src/app/router/routes/public.ts`

登录路由的定义大致如下：

```typescript
{
  path: ROUTE_PATH.LOGIN,
  name: ROUTE_NAME.LOGIN,
  component: () => import('@/pages/auth/LoginPage.vue'),
  meta: { publicOnly: true },
}
```

`publicOnly: true` 这个 meta 字段很关键，它告诉路由守卫：这个页面只给未登录用户看。已经登录的用户访问这个地址，守卫会把他们重定向走。

### 路由守卫如何处理

**文件:** `前端/src/app/router/guards.ts`

所有路由跳转都要经过 `handleAuthGuard()` 函数，它主要处理两个场景：

- `to.meta.publicOnly && isAuthenticated` — 已登录用户访问登录页，直接重定向到首页
- `to.meta.requiresAuth && !isAuthenticated` — 未登录用户访问需要认证的页面，跳转到登录页并在 URL 中附带 `redirect` 参数，方便登录后回到原页面

对于正常未登录用户访问 `/login`，两个条件都不满足，守卫直接放行，进入页面渲染。

### 页面组件

**文件:** `前端/src/pages/auth/LoginPage.vue`

这个组件很薄，按照 FSD 架构的约定，页面层只做编排，不写业务逻辑。它只做两件事：

1. 渲染 `LoginForm` 组件
2. 监听 `switchMode` 事件（用于切换到注册或忘记密码模式）

真正的逻辑在 `LoginForm` 里。

### 表单组件（核心）

**文件:** `前端/src/features/auth/ui/LoginForm.vue`

表单状态通过 Vue 的 `reactive` 管理：

```typescript
const form = reactive<LoginFormValues>({
  account: '',
  password: '',
  rememberMe: false,
})
```

验证逻辑有两个函数：

- `validateAccount` — 非空检查 + 最少 3 位字符
- `validatePassword` — 非空检查 + 最少 6 位字符

触发时机：输入框失焦时（`@blur`）做单字段验证，点击提交时做全量验证。

提交函数 `handleSubmit()` 按顺序执行以下步骤：

```typescript
async function handleSubmit() {
  submitError.value = ''                                              // 清除上次的错误提示
  if (!validateAll()) return                                          // 前端校验，不过不提交
  submitting.value = true                                             // 按钮变 loading 状态
  const result = await authApi.login(mapLoginFormToDto(form))        // 调用API
  authStore.setAuth(mapAuthRespToSession(result), { persistence })   // 更新全局状态
  await delay(900)                                                    // 短暂延迟让动画更自然
  toast.success('登录成功')
  router.push(redirect || HOME)                                       // 跳转
}
```

### DTO 转换

**文件:** `前端/src/features/auth/model/auth.mapper.ts`

`mapLoginFormToDto(form)` 把前端表单值转为后端期望的请求格式：

```typescript
{
  account: form.account.trim(),
  password: form.password,
  rememberMe: form.rememberMe,
}
```

trim() 去掉账号前后的空格，避免用户无意中输入空格导致登录失败。

**下一步看: `前端/src/shared/api/modules/auth.ts`**

---

## 第二步：请求发出 —— HTTP 请求如何构造和发送

### API 模块定义

**文件:** `前端/src/shared/api/modules/auth.ts`

```typescript
function login(payload: LoginReqDto): Promise<AuthRespDto> {
  return request.post<BackendAuthResp>('/auth/login', payload, {
    withAuth: false,  // 登录接口不需要带 token
  }).then(normalizeAuthResp)
}
```

注意 `withAuth: false` — 这是项目自定义的配置项，告诉请求层不要自动附加 `Authorization` 头。登录时用户还没有 token，所以不能带，带了反而可能造成混乱。

`.then(normalizeAuthResp)` 是在响应数据到达后做字段标准化（第七步会详细说明）。

### 请求封装层

**文件:** `前端/src/shared/api/request.ts`

`post<T>(url, data, config)` 的内部流程：

1. `mergeConfig(config)` — 如果 `withAuth === false`，设置内部标记 `__skipAuth: true`
2. `http.post()` — 调用 Axios 实例发送请求
3. `requestAndUnwrap()` — 拿到响应后调用 `unwrapApiResponse` 解包业务层信封

这一层把"发请求"和"解包响应"两个动作捆在一起，调用方拿到的直接是 `data` 字段里的内容，不需要自己判断 `code`。

### HTTP 客户端层

**文件:** `前端/src/shared/api/http.ts`

Axios 实例的基础配置：

- `baseURL = '/api/v1'`
- `timeout = 15000` 毫秒

**请求拦截器** `attachAuthToken()` 在每次请求发出前执行：

1. 检查 `__skipAuth` 标记，如果是登录请求，直接跳过
2. 否则从 `localStorage` 读取 token，拼成 `Authorization: Bearer <token>` 加入请求头

最终发出的登录请求长这样：

```
POST /api/v1/auth/login
Content-Type: application/json

{ "account": "user@example.com", "password": "123456", "rememberMe": true }
```

### Vite 开发代理

**文件:** `前端/vite.config.ts`

```typescript
proxy: {
  '/api': { target: 'http://localhost:8080' }
}
```

开发环境下，前端跑在 `localhost:5173`，后端网关跑在 `localhost:8080`。浏览器的同源策略不允许跨端口请求，Vite 代理帮你绕过去：前端发出的 `/api/v1/auth/login` 请求，会被 Vite 悄悄转发到 `http://localhost:8080/api/v1/auth/login`，前端代码不需要关心这个细节。

**注解说明:**

- `withAuth: false` — 不是 Axios 原生配置项，是项目自定义的扩展字段，通过 `__skipAuth` 内部标记实现跳过 token 注入
- Axios 拦截器 — 类似请求中间件，每次请求发出前和响应到达后都会执行，用于集中处理 token 附加和错误识别等通用逻辑

**下一步看: `后端 gateway-service GatewayAuthFilter.java`**

---

## 第三步：后端接收 —— 网关如何处理请求

### 网关全局过滤器

**文件:** `后端/gateway-service/src/main/java/com/platform/gateway/filter/GatewayAuthFilter.java`

这个过滤器实现了 `GlobalFilter + Ordered`，`order = -100` 代表最高优先级，所有进入网关的请求都先经过它。

处理流程分为几个阶段：

**第一阶段：清理和追踪**

1. 拦截 `/internal/**` 路径，直接返回 404，防止外部直接访问内部服务接口
2. 检查请求头中是否已有 `X-Trace-Id`，有则透传，无则生成新的 UUID 作为追踪 ID
3. 删除客户端自带的 `X-User-Id`、`X-Username`、`X-User-Role` 头，防止客户端伪造身份，然后重新注入合法的 TraceId

**第二阶段：白名单判断**

`isWhitelisted()` 检查当前路径是否在白名单中。`POST /api/v1/auth/login` 命中白名单，意味着这个接口不要求登录才能访问。

即使命中白名单，过滤器仍然会尝试解析请求头中的 token。这样做是为了支持"有 token 就带上身份信息，没 token 也能正常访问"的场景（比如已登录用户浏览公开文章）。

**第三阶段：白名单放行**

`handleWhitelistedRequest()` 无论 token 是否有效都放行请求，直接进入路由转发。登录请求在此步通过。

**第四阶段：路由转发**

按 GatewayRouteConfig 的配置，`/api/v1/auth/**` 转发到 `auth-service`（端口 8081）。

### 非白名单路径的处理逻辑

这部分登录请求不会走到，但了解一下有助于理解整个鉴权体系：

- 无 token 或 token 无效 — 返回 401 `{"code":401,"message":"Authentication required or token is invalid"}`
- token 有效但角色不够（如普通用户访问审核接口）— 返回 403 `{"code":403,"message":"没有审核权限"}`
- token 有效且权限足够 — 注入身份头后放行

### 身份头注入（认证成功时）

解析 JWT 得到 `JwtUser{ userId, username, role }` 后，网关会注入三个请求头传给下游服务：

```
X-User-Id: 1
X-Username: admin
X-User-Role: ADMIN
```

如果 token 快过期（`jwtHelper.shouldRefresh(token)` 返回 true），网关还会签发新 token，写入响应头 `New-Token`，前端拦截器会自动持久化它。

**注解说明:**

- `GlobalFilter` — Spring Cloud Gateway 的全局过滤器接口，实现它的 Bean 会对所有路由生效
- `Ordered` — 控制过滤器执行顺序，数值越小优先级越高，`-100` 表示非常高的优先级
- `Mono<Void>` — Project Reactor 的响应式类型，Gateway 基于 Netty 是全异步非阻塞的，所以用 Reactor 而不是传统的阻塞式返回值
- `AntPathMatcher` — Spring 的路径匹配工具，支持 `**` 通配符，如 `/api/v1/auth/**` 能匹配 `/api/v1/auth/login`

**下一步看: `后端 auth-service AuthController.java`**

---

## 第四步：Controller 接收 —— 参数如何校验

### AuthController

**文件:** `后端/auth-service/src/main/java/com/platform/auth/controller/AuthController.java`

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AuthResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }
}
```

这个 Controller 非常薄，它只做参数接收和格式包装，业务逻辑全部委托给 `authService.login()`。

### 参数校验

**文件:** `后端/auth-service/src/main/java/com/platform/auth/api/req/LoginReq.java`

```java
@Data
public class LoginReq {
    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;

    private boolean rememberMe = false;
}
```

`@Valid` 注解触发 Jakarta Validation 框架在方法执行前自动校验 `LoginReq` 对象：

- `account` 为空或空白字符串 — Spring 自动返回 400，响应体包含 "账号不能为空"
- `password` 为空或空白字符串 — 同上，"密码不能为空"

校验通过后，代码才进入 `authService.login(req)`。

### 统一响应包装

**文件:** `后端/platform-kernel/src/main/java/com/platform/kernel/util/Result.java`

```java
Result.ok(data)
// 生成: { "code": 200, "message": "success", "data": { ... } }
```

所有接口都用这个统一格式返回，前端约定 `code === 200` 表示业务成功。

**注解说明:**

- `@RestController` — 等价于 `@Controller + @ResponseBody`，返回值会自动序列化为 JSON
- `@RequestMapping` — 定义这个 Controller 的路径前缀，所有方法的路径都会拼在它后面
- `@PostMapping("/login")` — 只处理 POST 请求，完整路径是 `/api/v1/auth/login`
- `@Valid` — 触发参数校验，校验失败时 Spring 自动拦截并返回 400，不需要手写 if 判断
- `@RequestBody` — 把请求体的 JSON 自动反序列化为 Java 对象
- `@NotBlank` — 校验字符串非 null、非空字符串、非纯空格字符串
- `@Data` — Lombok 注解，自动生成 getter/setter/toString/equals/hashCode
- `@RequiredArgsConstructor` — Lombok，为所有 `final` 字段生成构造函数，Spring 用它做依赖注入

**下一步看: `后端 auth-service AuthServiceImpl.java`**

---

## 第五步：业务处理 —— Service 层核心逻辑

### AuthServiceImpl

**文件:** `后端/auth-service/src/main/java/com/platform/auth/service/impl/AuthServiceImpl.java`

这个类注入了四个依赖：

- `UserMapper` — 数据库操作
- `PasswordEncoder` — BCrypt 密码编码器
- `JwtHelper` — JWT 签发和解析工具
- `StringRedisTemplate` — Redis 操作（登录流程不用，注册或 token 黑名单场景会用）

`login()` 完整逻辑：

```java
public AuthResp login(LoginReq req) {
    // 1. 判断账号类型：包含 @ 按邮箱查，否则按用户名查
    User user = req.getAccount().contains("@")
        ? findByEmail(req.getAccount())
        : findByUsername(req.getAccount());

    // 2. 用户不存在，或密码不匹配，统一抛同一个异常（不区分哪种原因，防止枚举账号）
    if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
        throw BusinessException.badRequest("Invalid account or password");
    }

    // 3. 签发 JWT token，rememberMe 影响过期时长
    String token = jwtHelper.createToken(
        user.getId(), user.getUsername(), user.getRole().name(), req.isRememberMe()
    );

    // 4. 记录日志
    log.info("User logged in: userId={}, username={}, rememberMe={}",
        user.getId(), user.getUsername(), req.isRememberMe());

    // 5. 构建响应
    return buildAuthResp(token, user);
}
```

`buildAuthResp()` 把 `User` 实体和 token 组装成前端需要的响应格式：

```java
private AuthResp buildAuthResp(String token, User user) {
    return AuthResp.builder()
        .token(token)
        .userId(user.getId())
        .username(user.getUsername())
        .nickname(user.getNickname())
        .email(user.getEmail())
        .role(user.getRole())        // UserRole 枚举: USER 或 ADMIN
        .avatarUrl(user.getAvatarUrl())
        .build();
}
```

登录是纯本地操作，不调用任何第三方服务。对比之下，注册流程会额外调用 `TurnstileService` 做人机验证，这是两者的重要区别。

**注解说明:**

- `@Service` — Spring Bean 标记，表示这是服务层组件，Spring 会自动实例化并管理它的生命周期
- `@Slf4j` — Lombok，自动生成 `private static final Logger log = LoggerFactory.getLogger(...)`，直接用 `log.info()` 即可
- `passwordEncoder.matches(raw, encoded)` — BCrypt 的特性是单向哈希，无法反解，只能用原始密码重新哈希后比对
- `BusinessException.badRequest()` — 项目自定义异常，会被全局异常处理器捕获，统一返回 400 格式响应

**下一步看: `后端 auth-service UserMapper.java + User.java`**

---

## 第六步：数据操作 —— SQL 如何执行

### Entity 实体类

**文件:** `后端/auth-service/src/main/java/com/platform/auth/entity/User.java`

```java
@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String password;          // BCrypt 哈希，如 $2b$12$pzkM...
    private UserRole role;            // 枚举: USER / ADMIN
    private String avatarUrl;
    private String coverUrl;
    private String signature;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Mapper 接口

**文件:** `后端/auth-service/src/main/java/com/platform/auth/mapper/UserMapper.java`

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {}
```

继承 `BaseMapper<User>` 后，MyBatis Plus 自动提供 `selectById`、`selectOne`、`selectList`、`insert`、`updateById`、`deleteById` 等方法，不需要手写任何 SQL。

登录场景用到的是 `selectOne`，配合 `LambdaQueryWrapper` 构建条件查询：

```java
// 按用户名查
userMapper.selectOne(
    new LambdaQueryWrapper<User>().eq(User::getUsername, account)
)
```

### 实际执行的 SQL

按用户名查询：

```sql
SELECT id, username, nickname, email, password, role,
       avatar_url, cover_url, signature, created_at, updated_at
FROM users
WHERE username = 'xxx'
LIMIT 1
```

按邮箱查询：

```sql
SELECT id, username, nickname, email, password, role,
       avatar_url, cover_url, signature, created_at, updated_at
FROM users
WHERE email = 'xxx@example.com'
LIMIT 1
```

### 数据库表结构

**文件:** `后端/deploy/sql/init.sql`

表名 `users`，关键字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT AUTO_INCREMENT` | 主键，自增 |
| `username` | `VARCHAR(20) UNIQUE` | 唯一用户名 |
| `email` | `VARCHAR(100) UNIQUE` | 唯一邮箱 |
| `password` | `VARCHAR(100)` | BCrypt 哈希，如 `$2b$12$pzkM...` |
| `role` | `ENUM('USER','ADMIN')` | 用户角色 |

**注解说明:**

- `@TableName("users")` — MyBatis Plus 注解，指定这个实体类对应的数据库表名，默认会把驼峰类名转成下划线，加这个注解可以明确指定
- `@TableId(type = IdType.AUTO)` — 主键策略为数据库自增，插入时不需要手动赋值
- `BaseMapper<User>` — MyBatis Plus 核心接口，泛型指定实体类，框架在运行时自动生成 SQL 实现
- `LambdaQueryWrapper` — 类型安全的查询构造器，用 `User::getUsername` 这种方法引用代替字符串字段名，重构时不会漏掉
- `@Mapper` — MyBatis 注解，让 Spring 扫描到这个接口并自动创建代理实现类

**下一步看: 响应如何原路返回到前端**

---

## 第七步：前端处理响应 —— 数据解包 / 状态更新 / 路由跳转

### 响应数据格式（后端返回）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "username": "admin",
    "nickname": "admin",
    "email": "admin@example.com",
    "role": "ADMIN",
    "avatarUrl": null
  }
}
```

### Axios 响应拦截器

**文件:** `前端/src/shared/api/http.ts`

响应到达后，拦截器先做一件事：调用 `tryPersistRefreshedToken(response)`，检查响应头中是否有 `New-Token`、`x-new-token`、`x-access-token` 等字段。如果有，说明网关刷新了 token，前端把它存到 localStorage，下次请求自动用新 token。这个机制让用户无感知地保持登录状态。

### 响应解包

**文件:** `前端/src/shared/api/response.ts`

`unwrapApiResponse(payload)` 做三件事：

1. `isApiResponse(payload)` — 检查响应体是否有 `code`、`message`、`data` 三个字段
2. `isBusinessSuccess(code)` — 检查 `code === 200`
3. 成功时返回 `payload.data`（即 `AuthResp` 对象），失败时调用 `runApiSideEffects(code, message)` 后抛出 `ApiBusinessError`

经过这一步，调用方拿到的是干净的 `data`，不需要自己判断 `code`。

### 后端响应适配

**文件:** `前端/src/shared/api/adapters.ts`

`normalizeAuthResp(raw: BackendAuthResp): AuthRespDto` 做字段标准化：

```typescript
{
  token: raw.token,
  user: {
    id: raw.userId,
    username: raw.username,
    email: raw.email ?? null,
    role: raw.role ?? 'USER',
    avatarUrl: resolveAssetUrl(raw.avatarUrl),   // 拼接 CDN 前缀
    nickname: raw.nickname ?? raw.username,        // 空昵称用用户名兜底
  }
}
```

这一步处理后端字段命名和前端不一致的情况（如 `userId` 转 `id`），同时处理 null 值兜底，让后续代码不需要反复判空。

### Feature 层 DTO 到 VM 的转换

**文件:** `前端/src/features/auth/model/auth.mapper.ts`

`mapAuthRespToSession(dto: AuthRespDto): AuthSessionVm` 把 API 响应格式转成前端状态管理需要的格式：

```typescript
{
  token: dto.token,
  user: {
    id: dto.user.id,
    username: dto.user.username,
    nickname: dto.user.nickname ?? dto.user.username,
    avatar: dto.user.avatarUrl ?? null,
    role: dto.user.role === 'ADMIN' ? 'ADMIN' : 'USER',
  }
}
```

这里对 `role` 做了防御性处理：只有明确是 `'ADMIN'` 才赋管理员角色，其他所有情况都降级为普通用户，避免因后端返回意外值导致权限扩大。

### Auth Store 状态更新

**文件:** `前端/src/stores/auth.ts`

`authStore.setAuth(sessionVm, { persistence })` 按顺序执行：

1. `clearAuthScopedQueries()` — 清除旧的 Vue Query 缓存，避免新登录的用户看到上一个用户留下的缓存数据
2. `setToken(token, persistence)` — 根据 `rememberMe`，把 token 存到 `localStorage`（勾选了记住我）或 `sessionStorage`（关闭浏览器后失效）
3. `setUser(user, persistence)` — 用同样的持久化策略存用户信息
4. `clearAuthError()` — 清除可能存在的旧认证错误状态

### 路由跳转

回到 `LoginForm.vue` 的 `handleSubmit()`，状态更新完成后：

```typescript
await delay(900)  // 让 loading 动画多转一会儿，避免闪烁
toast.success('登录成功')

const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
if (redirect) {
  await router.push(redirect)                      // 跳回之前被拦截的页面
} else {
  await router.push({ name: ROUTE_NAME.HOME })    // 跳到首页
}
```

`redirect` 参数是路由守卫在拦截未登录用户时附加的，这样用户登录后能直接回到原来想去的页面，体验更自然。

### 错误处理

如果登录失败（密码错误、账号不存在等），后端返回非 200 的 `code`，`ApiBusinessError` 被抛出，`catch` 块接住：

```typescript
catch (error) {
  submitError.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
  toast.error(submitError.value)
  submitting.value = false   // 解除 loading，让用户可以重新提交
}
```

错误信息来自后端返回的 `message` 字段，如 "Invalid account or password"。前端不需要自己编写所有错误文案，直接展示后端的即可。

---

## 完整文件追踪清单（按数据流顺序）

| 步骤 | 层次 | 文件路径 | 关键动作 |
|------|------|----------|----------|
| 1 | 前端 路由 | `src/app/router/routes/public.ts` | 定义 /login 路由，标记 publicOnly |
| 2 | 前端 守卫 | `src/app/router/guards.ts` | publicOnly 检查，已登录重定向 |
| 3 | 前端 页面 | `src/pages/auth/LoginPage.vue` | 渲染 LoginForm |
| 4 | 前端 表单 | `src/features/auth/ui/LoginForm.vue` | 验证 + 提交 |
| 5 | 前端 转换 | `src/features/auth/model/auth.mapper.ts` | Form 转 DTO |
| 6 | 前端 API | `src/shared/api/modules/auth.ts` | authApi.login() |
| 7 | 前端 请求层 | `src/shared/api/request.ts` | post + unwrap |
| 8 | 前端 HTTP | `src/shared/api/http.ts` | Axios 实例 + 拦截器 |
| 9 | 代理 | `vite.config.ts` | /api 代理到 :8080 |
| 10 | 后端 网关 | `gateway-service/.../GatewayAuthFilter.java` | 白名单检查 + 放行 |
| 11 | 后端 控制器 | `auth-service/.../AuthController.java` | @Valid 参数校验 |
| 12 | 后端 服务 | `auth-service/.../AuthServiceImpl.java` | 账号查询 + 密码比对 + JWT 签发 |
| 13 | 后端 数据层 | `auth-service/.../UserMapper.java` | SQL 查询执行 |
| 14 | 后端 实体 | `auth-service/.../User.java` | ORM 映射 |
| 15 | 后端 响应 | `platform-kernel/.../Result.java` | 统一格式包装 |
| 16 | 前端 解包 | `src/shared/api/response.ts` | code===200 检查 |
| 17 | 前端 适配 | `src/shared/api/adapters.ts` | 字段标准化 + null 兜底 |
| 18 | 前端 映射 | `src/features/auth/model/auth.mapper.ts` | DTO 转 VM |
| 19 | 前端 状态 | `src/stores/auth.ts` | 存 token + 用户信息 |
| 20 | 前端 跳转 | `src/features/auth/ui/LoginForm.vue` | router.push |
