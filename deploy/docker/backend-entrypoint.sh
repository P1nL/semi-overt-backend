#!/bin/sh
set -eu

service_name="${SERVICE_NAME:-${1:-}}"
case "$service_name" in
  db-migration) main_class='com.platform.migration.DbMigrationApplication' ;;
  gateway-service) main_class='com.platform.gateway.GatewayServiceApplication' ;;
  auth-service) main_class='com.platform.auth.AuthServiceApplication' ;;
  content-service) main_class='com.platform.content.ContentServiceApplication' ;;
  review-service) main_class='com.platform.review.ReviewServiceApplication' ;;
  search-service) main_class='com.platform.search.SearchServiceApplication' ;;
  file-service) main_class='com.platform.file.FileServiceApplication' ;;
  notification-service) main_class='com.platform.notification.NotificationServiceApplication' ;;
  *)
    echo "Unsupported SERVICE_NAME: $service_name" >&2
    exit 64
    ;;
esac

if [ "${1:-}" = "$service_name" ]; then
  shift
fi

classes="/app/services/$service_name"
if [ ! -d "$classes" ]; then
  echo "Missing application classes: $classes" >&2
  exit 66
fi

# Dependencies shared by all services are stored once instead of being repeated
# inside eight Spring Boot fat jars.
# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} -cp "$classes:/app/service-libs/$service_name/*" "$main_class" "$@"
