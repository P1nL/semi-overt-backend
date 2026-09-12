# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -T 1 -B -DskipTests package \
    && mkdir -p /out/lib-store /out/service-libs /out/services \
    && for module in db-migration gateway-service auth-service content-service review-service search-service file-service notification-service; do \
         jar_path="$(find "/workspace/$module/target" -maxdepth 1 -type f -name "$module-*.jar" ! -name '*.original' | head -n 1)"; \
         test -n "$jar_path"; \
         extract_dir="/tmp/extract-$module"; \
         mkdir -p "$extract_dir" "/out/services/$module" "/out/service-libs/$module"; \
         cd "$extract_dir"; \
         jar -xf "$jar_path" BOOT-INF/classes BOOT-INF/lib; \
         cp -a BOOT-INF/classes/. "/out/services/$module/"; \
         for dependency in BOOT-INF/lib/*.jar; do \
           dependency_name="$(basename "$dependency")"; \
           target="/out/lib-store/$dependency_name"; \
           if [ -f "$target" ]; then \
             cmp -s "$dependency" "$target" || { echo "Dependency filename collision: $target" >&2; exit 1; }; \
           else \
             cp "$dependency" "$target"; \
           fi; \
           ln -s "../../lib-store/$dependency_name" "/out/service-libs/$module/$dependency_name"; \
         done; \
         cd /workspace; \
       done

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 semi \
    && useradd --system --uid 10001 --gid semi --home-dir /app --shell /usr/sbin/nologin semi \
    && mkdir -p /data/uploads \
    && chown semi:semi /data/uploads

WORKDIR /app
COPY --chown=semi:semi --from=build /out/ /app/
COPY --chown=semi:semi deploy/docker/backend-entrypoint.sh /app/backend-entrypoint.sh
RUN chmod 0555 /app/backend-entrypoint.sh

USER semi
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError" \
    SERVICE_NAME="gateway-service"
EXPOSE 8080 8081 8082 8083 8084 8085 8086
ENTRYPOINT ["/app/backend-entrypoint.sh"]
