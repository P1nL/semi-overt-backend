# 关键文件深度导读

适合谁看：需要快速定位关键代码文件的人。  
读完能解决什么问题：知道各条主线的核心文件在哪，而不是在整个仓库里全量扫一遍。

## 网关与鉴权

- [gateway-service/src/main/java/com/platform/gateway/config/GatewayRouteConfig.java](../../gateway-service/src/main/java/com/platform/gateway/config/GatewayRouteConfig.java)
- [gateway-service/src/main/java/com/platform/gateway/filter/GatewayAuthFilter.java](../../gateway-service/src/main/java/com/platform/gateway/filter/GatewayAuthFilter.java)
- [gateway-service/src/main/java/com/platform/gateway/controller/GatewayAuthController.java](../../gateway-service/src/main/java/com/platform/gateway/controller/GatewayAuthController.java)
- [platform-kernel/src/main/java/com/platform/kernel/constant/HeaderNames.java](../../platform-kernel/src/main/java/com/platform/kernel/constant/HeaderNames.java)

## 认证与用户

- [auth-service/src/main/java/com/platform/auth/controller/AuthController.java](../../auth-service/src/main/java/com/platform/auth/controller/AuthController.java)
- [auth-service/src/main/java/com/platform/auth/controller/UserController.java](../../auth-service/src/main/java/com/platform/auth/controller/UserController.java)
- [auth-service/src/main/java/com/platform/auth/controller/internal/InternalUserController.java](../../auth-service/src/main/java/com/platform/auth/controller/internal/InternalUserController.java)
- [auth-service/src/main/java/com/platform/auth/config/SecurityConfig.java](../../auth-service/src/main/java/com/platform/auth/config/SecurityConfig.java)

## 内容与文章生命周期

- [content-service/src/main/java/com/platform/content/controller/ArticleController.java](../../content-service/src/main/java/com/platform/content/controller/ArticleController.java)
- [content-service/src/main/java/com/platform/content/controller/HomeController.java](../../content-service/src/main/java/com/platform/content/controller/HomeController.java)
- [content-service/src/main/java/com/platform/content/controller/CategoryController.java](../../content-service/src/main/java/com/platform/content/controller/CategoryController.java)
- [content-service/src/main/java/com/platform/content/controller/internal/InternalArticleController.java](../../content-service/src/main/java/com/platform/content/controller/internal/InternalArticleController.java)
- [content-service/src/main/java/com/platform/content/config/SecurityConfig.java](../../content-service/src/main/java/com/platform/content/config/SecurityConfig.java)

## 审核链路

- [review-service/src/main/java/com/platform/review/controller/ReviewController.java](../../review-service/src/main/java/com/platform/review/controller/ReviewController.java)
- [review-service/src/main/java/com/platform/review/controller/internal/InternalReviewController.java](../../review-service/src/main/java/com/platform/review/controller/internal/InternalReviewController.java)
- [review-service/src/main/java/com/platform/review/config/SecurityConfig.java](../../review-service/src/main/java/com/platform/review/config/SecurityConfig.java)

## 搜索、通知与事件

- [search-service/src/main/java/com/platform/search/controller/SearchController.java](../../search-service/src/main/java/com/platform/search/controller/SearchController.java)
- [notification-service/src/main/java/com/platform/notification/mq/ArticleStatusChangedEventListener.java](../../notification-service/src/main/java/com/platform/notification/mq/ArticleStatusChangedEventListener.java)
- [platform-kernel/src/main/java/com/platform/kernel/constant/EventConstants.java](../../platform-kernel/src/main/java/com/platform/kernel/constant/EventConstants.java)
- [platform-events/src/main/java/com/platform/events/config/RabbitEventConfig.java](../../platform-events/src/main/java/com/platform/events/config/RabbitEventConfig.java)

## 运行与交付

- [scripts/dev-up.ps1](../../scripts/dev-up.ps1)
- [scripts/smoke-test.ps1](../../scripts/smoke-test.ps1)
- [scripts/run-service.sh](../../scripts/run-service.sh)
- [scripts/env/server.env.example](../../scripts/env/server.env.example)
- [docker-compose.yml](../../docker-compose.yml)
- [deploy/nginx/now-demo.conf](../../deploy/nginx/now-demo.conf)
- [architecture-tests/src/test/java/com/platform/architecture/FinalArchitectureTest.java](../../architecture-tests/src/test/java/com/platform/architecture/FinalArchitectureTest.java)

## 如何使用这份附录

- 想看接口：先回到 [API 与权限矩阵](../04-reference/01-api-and-permissions.md)
- 想看边界：先回到 [模块边界](../02-architecture/01-module-boundaries.md)
- 想看配置：先回到 [配置来源与运行依赖](../03-development-and-operations/02-configuration-and-dependencies.md)

这份附录只负责“快速定位关键文件”，不替代主题文档。
