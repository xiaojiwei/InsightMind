#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AD_DIR="$ROOT_DIR/apps/ad"
DA_DIR="$ROOT_DIR/apps/da"
LOG_DIR="$ROOT_DIR/logs"

AD_LABEL="insightmind-ad"
DA_LABEL="insightmind-da"
AD_PORT="${INSIGHTMIND_AD_PORT:-8080}"
DA_PORT="${INSIGHTMIND_DA_PORT:-8091}"
AD_LOG="$LOG_DIR/ad.log"
DA_LOG="$LOG_DIR/da.log"
DEMO_OUTPUT_DIR="$ROOT_DIR/demo/default/ad/output"

python_has_ad_deps() {
  local python_bin="$1"
  [[ -x "$python_bin" ]] || return 1
  "$python_bin" - <<'PY' >/dev/null 2>&1
import fastapi
import uvicorn
import sqlglot
import rdflib
import pandas
PY
}

AD_PYTHON="${INSIGHTMIND_AD_PYTHON:-$AD_DIR/venv/bin/python}"
if ! python_has_ad_deps "$AD_PYTHON"; then
  if python_has_ad_deps "/Users/xiao/GraphBuilder/venv/bin/python"; then
    AD_PYTHON="/Users/xiao/GraphBuilder/venv/bin/python"
  else
    AD_PYTHON="$(command -v python3 || command -v python)"
  fi
fi

JAVA_BIN="${INSIGHTMIND_JAVA:-/Library/Java/JavaVirtualMachines/jdk1.8.0_321.jdk/Contents/Home/bin/java}"
if [[ ! -x "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java)"
fi

DA_JAR="$DA_DIR/target/da-indicator-0.0.1-SNAPSHOT.jar"
KG_PATH="$AD_DIR/output/business_kg/indicator-data.ttl"

kg_value() {
  local prop="$1"
  local file="$2"
  [[ -f "$file" ]] || return 0
  awk -v prop="ind:${prop}" '
    $1 == prop && $2 ~ /^"/ {
      value = $0
      sub("^[[:space:]]*" prop "[[:space:]]+", "", value)
      sub("[[:space:]]*;[[:space:]]*$", "", value)
      sub("[[:space:]]*\\.[[:space:]]*$", "", value)
      gsub(/^"|"$/, "", value)
      print value
      exit
    }
  ' "$file"
}

ensure_demo_assets() {
  if [[ -f "$KG_PATH" ]]; then
    return
  fi
  if [[ ! -d "$DEMO_OUTPUT_DIR" ]]; then
    return
  fi

  mkdir -p "$AD_DIR/output"
  cp -R "$DEMO_OUTPUT_DIR"/. "$AD_DIR/output"/
}

usage() {
  echo "Usage:"
  echo "  $0 {start|stop|restart|status} [ad|da|all]"
  echo "  $0 setup [core|analysis|db-extra|dev|full]"
}

target_or_all() {
  local target="${1:-all}"
  case "$target" in
    ad|da|all) echo "$target" ;;
    *) usage; exit 2 ;;
  esac
}

pids_on_port() {
  local port="$1"
  lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
}

is_running() {
  local port="$1"
  [[ -n "$(pids_on_port "$port")" ]]
}

job_exists() {
  local label="$1"
  launchctl list 2>/dev/null | awk '{print $3}' | grep -Fxq "$label"
}

wait_for_port() {
  local name="$1"
  local port="$2"

  for _ in {1..180}; do
    if is_running "$port"; then
      echo "$name is listening on port $port"
      return
    fi
    sleep 1
  done

  echo "$name did not start listening on port $port within 60 seconds" >&2
  return 1
}

ad_requirements_file() {
  local profile="${1:-core}"
  case "$profile" in
    core) echo "$AD_DIR/requirements-core.txt" ;;
    analysis) echo "$AD_DIR/requirements-analysis.txt" ;;
    db-extra) echo "$AD_DIR/requirements-db-extra.txt" ;;
    dev) echo "$AD_DIR/requirements-dev.txt" ;;
    full) echo "$AD_DIR/requirements-full.txt" ;;
    *) echo "Unknown AD dependency profile: $profile" >&2; usage; exit 2 ;;
  esac
}

setup_ad() {
  local profile="${1:-core}"
  local req
  req="$(ad_requirements_file "$profile")"

  if [[ ! -d "$AD_DIR/venv" ]]; then
    python3 -m venv "$AD_DIR/venv"
  fi

  "$AD_DIR/venv/bin/python" -m pip install --upgrade pip
  "$AD_DIR/venv/bin/python" -m pip install -r "$req"
  echo "Installed AD dependency profile '$profile' from $req"
}

submit_job() {
  local label="$1"
  local log_path="$2"
  local command="$3"

  mkdir -p "$LOG_DIR"
  if job_exists "$label"; then
    launchctl remove "$label" >/dev/null 2>&1 || true
    sleep 1
  fi

  launchctl submit -l "$label" -o "$log_path" -e "$log_path" -- /bin/zsh -lc "$command"
}

propagate_ad_llm_env() {
  if [[ -f "$AD_DIR/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$AD_DIR/.env"
    set +a
  fi

  local name value
  for name in \
    DEEPSEEK_API_KEY DEEPSEEK_BASE_URL DEEPSEEK_MODEL_NAME \
    LLM_API_KEY LLM_BASE_URL LLM_MODEL_NAME \
    GPT55_API_KEY GPT55_BASE_URL GPT55_MODEL_NAME \
    OPENAI_API_KEY OPENAI_BASE_URL OPENAI_MODEL OPENAI_MODEL_NAME \
    BUSINESS_KG_MODEL; do
    value="${!name:-}"
    if [[ -n "$value" ]]; then
      launchctl setenv "$name" "$value" >/dev/null 2>&1 || true
    fi
  done

  if [[ -z "${DEEPSEEK_API_KEY:-}" && -n "${LLM_API_KEY:-}" ]]; then
    launchctl setenv DEEPSEEK_API_KEY "$LLM_API_KEY" >/dev/null 2>&1 || true
  elif [[ -z "${DEEPSEEK_API_KEY:-}" && -n "${OPENAI_API_KEY:-}" ]]; then
    launchctl setenv DEEPSEEK_API_KEY "$OPENAI_API_KEY" >/dev/null 2>&1 || true
  fi
  if [[ -z "${DEEPSEEK_BASE_URL:-}" && -n "${LLM_BASE_URL:-}" ]]; then
    launchctl setenv DEEPSEEK_BASE_URL "$LLM_BASE_URL" >/dev/null 2>&1 || true
  elif [[ -z "${DEEPSEEK_BASE_URL:-}" && -n "${OPENAI_BASE_URL:-}" ]]; then
    launchctl setenv DEEPSEEK_BASE_URL "$OPENAI_BASE_URL" >/dev/null 2>&1 || true
  elif [[ -z "${DEEPSEEK_BASE_URL:-}" ]]; then
    launchctl unsetenv DEEPSEEK_BASE_URL >/dev/null 2>&1 || true
  fi
  if [[ -z "${DEEPSEEK_MODEL_NAME:-}" && -n "${LLM_MODEL_NAME:-}" ]]; then
    launchctl setenv DEEPSEEK_MODEL_NAME "$LLM_MODEL_NAME" >/dev/null 2>&1 || true
  elif [[ -z "${DEEPSEEK_MODEL_NAME:-}" && -n "${OPENAI_MODEL_NAME:-}" ]]; then
    launchctl setenv DEEPSEEK_MODEL_NAME "$OPENAI_MODEL_NAME" >/dev/null 2>&1 || true
  elif [[ -z "${DEEPSEEK_MODEL_NAME:-}" && -n "${OPENAI_MODEL:-}" ]]; then
    launchctl setenv DEEPSEEK_MODEL_NAME "$OPENAI_MODEL" >/dev/null 2>&1 || true
  elif [[ -z "${DEEPSEEK_MODEL_NAME:-}" ]]; then
    launchctl unsetenv DEEPSEEK_MODEL_NAME >/dev/null 2>&1 || true
  fi
}

start_ad() {
  ensure_demo_assets

  if is_running "$AD_PORT"; then
    echo "AD already running on http://localhost:$AD_PORT"
    return
  fi

  if ! python_has_ad_deps "$AD_PYTHON"; then
    echo "AD core dependencies are missing for Python: $AD_PYTHON" >&2
    echo "Run: $0 setup" >&2
    echo "Optional profiles: $0 setup analysis | db-extra | dev | full" >&2
    return 1
  fi

  propagate_ad_llm_env
  submit_job "$AD_LABEL" "$AD_LOG" "cd '$AD_DIR' && exec '$AD_PYTHON' -u web_app.py"
  echo "Started AD -> http://localhost:$AD_PORT"
  echo "AD log: $AD_LOG"
  wait_for_port "AD" "$AD_PORT"
}

start_da() {
  ensure_demo_assets

  if is_running "$DA_PORT"; then
    echo "DA already running on http://localhost:$DA_PORT"
    return
  fi

  if [[ ! -f "$DA_JAR" ]]; then
    echo "Missing DA jar: $DA_JAR" >&2
    echo "Run: cd $DA_DIR && mvn -DskipTests package" >&2
    return 1
  fi

  local da_mysql_user da_mysql_password da_mysql_url
  da_mysql_user="${INSIGHTMIND_DA_MYSQL_USER:-${MYSQL_USER:-$(kg_value dbUser "$KG_PATH")}}"
  da_mysql_password="${INSIGHTMIND_DA_MYSQL_PASSWORD:-${MYSQL_PASSWORD:-$(kg_value dbPassword "$KG_PATH")}}"
  da_mysql_user="${da_mysql_user:-root}"
  da_mysql_password="${da_mysql_password:-root}"
  da_mysql_url="${INSIGHTMIND_DA_MYSQL_URL:-jdbc:mysql://127.0.0.1:3306/indbtest?allowMultiQueries=true&useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&autoReconnect=true&failOverReadOnly=false&maxReconnects=30&initialTimeout=2&connectTimeout=3000}"

  submit_job "$DA_LABEL" "$DA_LOG" "cd '$DA_DIR' && exec '$JAVA_BIN' -jar '$DA_JAR' --spring.config.additional-location='file:$DA_DIR/application-local.yml' --spring.profiles.active=dev --server.port='$DA_PORT' --spring.datasource.dynamic.primary=mysql --spring.datasource.dynamic.datasource.mysql.url='$da_mysql_url' --spring.datasource.dynamic.datasource.mysql.driver-class-name=com.mysql.cj.jdbc.Driver --spring.datasource.dynamic.datasource.mysql.username='$da_mysql_user' --spring.datasource.dynamic.datasource.mysql.password='$da_mysql_password' --indicator.graph.data-path='$KG_PATH'"
  echo "Started DA -> http://localhost:$DA_PORT"
  echo "DA log: $DA_LOG"
  wait_for_port "DA" "$DA_PORT"
}

stop_label() {
  local label="$1"
  if job_exists "$label"; then
    launchctl remove "$label" >/dev/null 2>&1 || true
  fi
}

stop_port() {
  local name="$1"
  local port="$2"
  local pids
  pids="$(pids_on_port "$port")"

  if [[ -z "$pids" ]]; then
    echo "$name is not running on port $port"
    return
  fi

  echo "Stopping $name on port $port: $pids"
  kill $pids 2>/dev/null || true

  for _ in {1..20}; do
    if ! is_running "$port"; then
      echo "Stopped $name"
      return
    fi
    sleep 0.5
  done

  echo "$name did not stop gracefully; forcing stop"
  kill -9 $pids 2>/dev/null || true
}

stop_ad() {
  stop_label "$AD_LABEL"
  sleep 1
  stop_port "AD" "$AD_PORT"
}

stop_da() {
  stop_label "$DA_LABEL"
  sleep 1
  stop_port "DA" "$DA_PORT"
}

status_one() {
  local name="$1"
  local label="$2"
  local port="$3"
  local url="$4"
  local pids
  pids="$(pids_on_port "$port")"

  if [[ -n "$pids" ]]; then
    if job_exists "$label"; then
      echo "$name running: $url (pid $pids, launchctl $label)"
    else
      echo "$name running: $url (pid $pids)"
    fi
  else
    echo "$name stopped: $url"
  fi
}

start_target() {
  case "$1" in
    ad) start_ad ;;
    da) start_da ;;
    all) start_ad; start_da ;;
  esac
}

stop_target() {
  case "$1" in
    ad) stop_ad ;;
    da) stop_da ;;
    all) stop_ad; stop_da ;;
  esac
}

status_target() {
  case "$1" in
    ad) status_one "AD" "$AD_LABEL" "$AD_PORT" "http://localhost:$AD_PORT" ;;
    da) status_one "DA" "$DA_LABEL" "$DA_PORT" "http://localhost:$DA_PORT" ;;
    all)
      status_one "AD" "$AD_LABEL" "$AD_PORT" "http://localhost:$AD_PORT"
      status_one "DA" "$DA_LABEL" "$DA_PORT" "http://localhost:$DA_PORT"
      ;;
  esac
}

main() {
  local action="${1:-}"
  local target

  if [[ "$action" == "setup" ]]; then
    setup_ad "${2:-core}"
    return
  fi

  target="$(target_or_all "${2:-all}")"

  case "$action" in
    start) start_target "$target" ;;
    stop) stop_target "$target" ;;
    restart)
      stop_target "$target"
      start_target "$target"
      ;;
    status) status_target "$target" ;;
    *) usage; exit 2 ;;
  esac
}

main "$@"
