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

JAVA_BIN="${INSIGHTMIND_JAVA:-/opt/homebrew/Cellar/openjdk@11/11.0.27/bin/java}"
if [[ ! -x "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java)"
fi

DA_JAR="$DA_DIR/target/da-indicator-0.0.1-SNAPSHOT.jar"
KG_PATH="$AD_DIR/output/business_kg/indicator-data.ttl"

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
  echo "Usage: $0 {start|stop|restart|status} [ad|da|all]"
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

  for _ in {1..60}; do
    if is_running "$port"; then
      echo "$name is listening on port $port"
      return
    fi
    sleep 1
  done

  echo "$name did not start listening on port $port within 60 seconds" >&2
  return 1
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

start_ad() {
  ensure_demo_assets

  if is_running "$AD_PORT"; then
    echo "AD already running on http://localhost:$AD_PORT"
    return
  fi

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

  submit_job "$DA_LABEL" "$DA_LOG" "cd '$DA_DIR' && exec '$JAVA_BIN' -jar '$DA_JAR' --spring.profiles.active=dev --server.port='$DA_PORT' --indicator.graph.data-path='$KG_PATH'"
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
