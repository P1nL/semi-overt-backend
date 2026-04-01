# 端口、依赖与队列清单

适合谁看：需要查运行端口、服务依赖、RabbitMQ 队列和交换机的人。  
读完能解决什么问题：快速完成环境核对、联调准备和消息链路排查。

## 业务服务端口

- `gateway-service`：`8080`
- `auth-service`：`8081`
- `content-service`：`8082`
- `review-service`：`8083`
- `search-service`：`8084`
- `file-service`：`8085`
- `notification-service`：`8086`

## 中间件端口

- MySQL：`3306`
- Redis：`6379`
- Nacos HTTP：`8848`
- Nacos gRPC：`9848`
- RabbitMQ：`5672`
- RabbitMQ 管理台：`15672`

## 服务依赖速查

- `gateway-service`：Redis、Nacos
- `auth-service`：MySQL、Redis、Nacos、邮件服务
- `content-service`：MySQL、Redis、RabbitMQ、Nacos
- `review-service`：MySQL、RabbitMQ、Nacos
- `search-service`：MySQL、RabbitMQ、Nacos
- `file-service`：Nacos、文件系统路径
- `notification-service`：MySQL、RabbitMQ、Nacos

## 事件队列

定义在 [EventConstants.java](../../platform-kernel/src/main/java/com/platform/kernel/constant/EventConstants.java)：

- `article.submitted.review`
- `review.decided.content`
- `article.status.changed.review`
- `article.status.changed.notification`
- `article.status.changed.search`

## 事件交换机

定义在 [RabbitEventConfig.java](../../platform-events/src/main/java/com/platform/events/config/RabbitEventConfig.java) 的基础事件交换机：

- `article.submitted.exchange`
- `review.decided.exchange`
- `article.status.changed.exchange`

每个主队列还会派生：

- `<queue>.main.exchange`
- `<queue>.retry.exchange`
- `<queue>.dlq.exchange`
- `<queue>.retry`
- `<queue>.dlq`

## 关键消费者

- `review-service` 消费 `article.submitted.review`
- `content-service` 消费 `review.decided.content`
- `notification-service` 消费 `article.status.changed.notification`
- `search-service` 消费 `article.status.changed.search`

## 本地文件与静态访问

- 文件上传根路径默认：`E:/nowdata/app/uploads`
- 访问前缀默认：`/static/uploads`

部署到 Linux 时，建议通过环境变量改成服务器路径，而不是沿用本地盘符。
