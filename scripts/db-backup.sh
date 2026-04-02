#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="${1:-./backups}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"

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

if [[ ! "${DB_URL}" =~ ^jdbc:mysql://([^:/?,]+)(:([0-9]+))?/([^?]+) ]]; then
  echo "Unsupported DB_URL format: ${DB_URL}" >&2
  exit 1
fi

HOST_NAME="${BASH_REMATCH[1]}"
PORT="${BASH_REMATCH[3]:-3306}"
DATABASE="${BASH_REMATCH[4]}"

mkdir -p "${OUTPUT_DIR}"
BACKUP_FILE="${OUTPUT_DIR}/${DATABASE}-$(date +%Y%m%d%H%M%S).sql"

MYSQL_PWD="${DB_PASSWORD}" "${MYSQLDUMP_BIN}" \
  --host="${HOST_NAME}" \
  --port="${PORT}" \
  --user="${DB_USERNAME}" \
  --single-transaction \
  --routines \
  --events \
  "${DATABASE}" > "${BACKUP_FILE}"

echo "Database backup created: ${BACKUP_FILE}"
