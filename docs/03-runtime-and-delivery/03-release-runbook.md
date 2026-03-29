# 03 上线流程 Runbook

## 1. 上线目标与适用范围

这份文档适用于当前仓库已经支持的正式发布基线：

- Linux 主机
- 业务服务通过 `java -jar` 运行
- 配置通过环境变量与 Nacos 提供
- MySQL、Redis、RabbitMQ、Elasticsearch、Nacos 为集中中间件
- 可选 Nginx 作为外层 HTTP 入口

这份文档不把 Kubernetes、业务服务 Docker 化部署或完整 CI/CD 当成当前默认事实。

## 2. 上线前准备

### 2.1 代码冻结

先确定本次发布对应的代码版本，不在发布过程中继续混入新的功能提交。

含义：

- 避免运行中的配置、脚本、Jar 和排障记录对应不上同一个版本

### 2.2 配置核对

核对以下信息：

- 环境变量
- Nacos `group`、`namespace`、dataId
- 数据库连接
- Redis、RabbitMQ、Elasticsearch 连接
- JWT 密钥
- 文件存储路径或对象存储准备情况

含义：

- 多服务系统上线失败最常见的原因不是代码编不过，而是运行参数和目标环境不一致

### 2.3 数据备份

至少确认：

- MySQL 可回滚备份存在
- Elasticsearch 索引可重建策略明确
- Nacos 配置有备份或导出

含义：

- 回滚不只是换回旧 Jar，还包括把环境恢复到可运行状态

### 2.4 中间件连通性预检查

确认：

- MySQL 可访问
- Redis 可访问
- RabbitMQ 可访问
- Elasticsearch 可访问
- Nacos 可访问

## 3. 构建与制品确认

在仓库根目录执行：

```bash
mvn clean package
```

确认每个模块 `target/` 下都生成了对应可运行 Jar，且不是旧包或 `*.original` 包。

这项工作的含义：

- 避免实际发布了旧包或错误模块

## 4. 环境初始化

### 4.1 检查主机基础条件

确认：

- JDK 17 可用
- 目标端口未被占用
- 防火墙或安全组规则符合预期

### 4.2 准备 Nacos

确认：

- `NACOS_SERVER_ADDR` 正确
- `NACOS_NAMESPACE` 正确
- 共享和服务级 dataId 已存在

### 4.3 检查 JWT 一致性

确认网关与认证服务读取的是同一套 JWT 关键配置。

### 4.4 文件存储准备

如果继续采用当前本地文件系统基线，确认：

- `STORAGE_UPLOAD_PATH` 指向的目录存在且可写
- `STORAGE_ACCESS_PREFIX` 与网关/Nginx 静态代理路径一致

## 5. 服务发布顺序

当前发布顺序固定为：

1. `auth-service`
2. `content-service`
3. `review-service`
4. `search-service`
5. `notification-service`
6. `file-service`
7. `gateway-service`

推荐使用：

```bash
./scripts/run-service.sh <service-name> server <env-file>
```

这个顺序的含义：

- 先启动被依赖的真源和业务服务
- 最后启动统一公网入口

## 6. 启动后验证

### 6.1 服务级健康检查

每个服务都直接检查：

- `/actuator/health`
- `/actuator/info`

### 6.2 Nacos 注册检查

在 Nacos 中确认预期实例已注册。

### 6.3 网关入口检查

至少检查：

- 匿名访问 `GET /api/v1/home`
- 受保护路由在无 token 时返回 `401`
- 无效 token 返回 `401`

### 6.4 关键业务检查

至少覆盖：

- 登录是否成功
- 创建文章是否成功
- 提交审核是否成功
- 审核通过后通知是否落库
- 审核通过后搜索是否可见

“启动成功”不等于“业务可用”，这一步就是把两者区分开。

## 7. 烟雾测试与业务验收

当前建议直接对齐 [smoke-test.ps1](../../scripts/smoke-test.ps1) 的验收能力。

它覆盖的主线是：

`注册 -> 登录 -> 创建文章 -> 保存草稿 -> 提交审核 -> 审核通过 -> 通知落库 -> 搜索可见`

为什么要覆盖这条链路：

- 因为它同时验证了网关鉴权、内容状态机、审核动作、MQ、通知和搜索投影

## 8. 回滚策略

### 8.1 何时回滚

满足以下任一条件，应进入回滚判断：

- 服务无法稳定启动
- 关键公开接口大面积异常
- 无效 token、鉴权、路由语义异常
- 提交审核、审核通过、通知、搜索主链路失败

### 8.2 回滚到哪里

回滚到上一个已验证可用版本的 Jar 与配置组合，而不是只回滚其中一部分。

### 8.3 分对象回滚

- Jar 回滚：替换为上一个稳定版本的构建产物
- 配置回滚：恢复 Nacos 配置和环境变量
- Nginx 回滚：恢复上一个稳定 `now-demo.conf`
- 搜索恢复：优先通过索引回填或重建恢复，不直接回滚文章主状态

### 8.4 回滚后验证

回滚后必须重新执行：

- 服务级健康检查
- 网关入口检查
- 关键业务链路检查

## 9. 常见故障排查

- 启动失败：先查环境变量、Nacos、端口占用、中间件连通性
- Nacos 配置缺失：先查 `group`、`namespace`、dataId
- 无效 token：先查 JWT 密钥和网关配置
- 搜索不可见：先查 ES、`search-service`、索引回填和文章状态
- 通知未落库：先查 `notification-service`、MQ 和事件投递
- 上传不可访问：先查存储目录、访问前缀和静态代理
