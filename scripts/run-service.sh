#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/run-service.sh <service-name> [profile] [env-file]

Starts a packaged Spring Boot service with java -jar.

Arguments:
  service-name  Module directory name, for example gateway-service
  profile       Spring profile to run, default: server
  env-file      Optional shell env file to source before startup
EOF
}

if [[ $# -lt 1 || $# -gt 3 ]]; then
  usage
  exit 1
fi

SERVICE_NAME="$1"
RUN_PROFILE="${2:-server}"
ENV_FILE="${3:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVICE_DIR="${REPO_ROOT}/${SERVICE_NAME}"
RUNTIME_ROOT="${REPO_ROOT}/.runtime"
PID_DIR="${RUNTIME_ROOT}/pids"
LOG_DIR="${RUNTIME_ROOT}/logs"
PID_FILE="${PID_DIR}/${SERVICE_NAME}.pid"
LOG_FILE="${LOG_DIR}/${SERVICE_NAME}.log"

if [[ ! -d "${SERVICE_DIR}" ]]; then
  echo "Unknown service directory: ${SERVICE_DIR}" >&2
  exit 1
fi

if [[ -n "${ENV_FILE}" ]]; then
  if [[ ! -f "${ENV_FILE}" ]]; then
    echo "Env file not found: ${ENV_FILE}" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

if ! command -v java >/dev/null 2>&1; then
  echo "java not found in PATH" >&2
  exit 1
fi

require_env() {
  local key="$1"
  if [[ -z "${!key:-}" ]]; then
    echo "Missing required environment variable: ${key}" >&2
    exit 1
  fi
}

require_env NACOS_SERVER_ADDR
require_env NACOS_NAMESPACE

mkdir -p "${PID_DIR}" "${LOG_DIR}"

if [[ -f "${PID_FILE}" ]]; then
  EXISTING_PID="$(cat "${PID_FILE}")"
  if [[ -n "${EXISTING_PID}" ]] && kill -0 "${EXISTING_PID}" >/dev/null 2>&1; then
    echo "${SERVICE_NAME} is already running with PID ${EXISTING_PID}" >&2
    exit 1
  fi
  rm -f "${PID_FILE}"
fi

shopt -s nullglob
JAR_CANDIDATES=("${SERVICE_DIR}/target/"*.jar)
shopt -u nullglob

JAR_PATH=""
for candidate in "${JAR_CANDIDATES[@]}"; do
  case "${candidate}" in
    *.original) continue ;;
  esac
  if [[ -n "${JAR_PATH}" ]]; then
    echo "Multiple runnable jars found under ${SERVICE_DIR}/target" >&2
    exit 1
  fi
  JAR_PATH="${candidate}"
done

if [[ -z "${JAR_PATH}" ]]; then
  echo "No runnable jar found under ${SERVICE_DIR}/target. Run 'mvn clean package' first." >&2
  exit 1
fi

SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-${RUN_PROFILE}}"
export SPRING_PROFILES_ACTIVE

echo "Starting ${SERVICE_NAME}"
echo "  jar: ${JAR_PATH}"
echo "  profile: ${SPRING_PROFILES_ACTIVE}"
echo "  log: ${LOG_FILE}"

nohup java -jar "${JAR_PATH}" --spring.profiles.active="${SPRING_PROFILES_ACTIVE}" >>"${LOG_FILE}" 2>&1 &
SERVICE_PID=$!
echo "${SERVICE_PID}" > "${PID_FILE}"

echo "Started ${SERVICE_NAME} with PID ${SERVICE_PID}"
echo "PID file: ${PID_FILE}"
echo "Log file: ${LOG_FILE}"
