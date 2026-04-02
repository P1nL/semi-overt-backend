#!/usr/bin/env bash
set -euo pipefail

RUN_PROFILE="${1:-prod}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ -z "${DB_URL:-}" ]]; then
  echo "DB_URL is required" >&2
  exit 1
fi
if [[ -z "${DB_USERNAME:-}" ]]; then
  echo "DB_USERNAME is required" >&2
  exit 1
fi
if [[ -z "${DB_PASSWORD+x}" ]]; then
  echo "DB_PASSWORD is required" >&2
  exit 1
fi

cd "${REPO_ROOT}"

./mvnw -pl db-migration -am -DskipTests package

JAR_PATH="$(find db-migration/target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)"
if [[ -z "${JAR_PATH}" ]]; then
  echo "Cannot find db-migration runnable jar" >&2
  exit 1
fi

SPRING_PROFILES_ACTIVE="${RUN_PROFILE}" java -jar "${JAR_PATH}"
echo "Flyway migration finished successfully"
