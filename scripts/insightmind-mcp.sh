#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV_DIR="${INSIGHTMIND_MCP_VENV:-$ROOT_DIR/.venv-mcp}"
PYTHON_BIN="$VENV_DIR/bin/python"
BASE_PYTHON="${INSIGHTMIND_MCP_SETUP_PYTHON:-}"
SERVER_FILE="$ROOT_DIR/apps/agent_gateway/insightmind_mcp.py"
REQUIREMENTS_FILE="$ROOT_DIR/apps/agent_gateway/requirements.txt"
LOG_DIR="$ROOT_DIR/logs"
LOG_FILE="$LOG_DIR/mcp.log"
LABEL="insightmind-mcp"
PORT="${INSIGHTMIND_MCP_PORT:-8092}"
HOST="${INSIGHTMIND_MCP_HOST:-127.0.0.1}"

usage() {
  echo "Usage: $0 {setup|start|stop|restart|status|stdio}"
}

pids_on_port() {
  lsof -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null || true
}

job_exists() {
  launchctl list 2>/dev/null | awk '{print $3}' | grep -Fxq "$LABEL"
}

check_runtime() {
  if [[ ! -x "$PYTHON_BIN" ]]; then
    echo "MCP runtime is not installed. Run: $0 setup" >&2
    return 1
  fi
  "$PYTHON_BIN" -c 'import httpx, mcp' >/dev/null 2>&1 || {
    echo "MCP dependencies are incomplete. Run: $0 setup" >&2
    return 1
  }
}

pip_install() {
  if "$PYTHON_BIN" -m pip install "$@"; then
    return
  fi
  echo "Retrying dependency installation without shell proxy variables..." >&2
  env -u ALL_PROXY -u all_proxy -u HTTP_PROXY -u http_proxy -u HTTPS_PROXY -u https_proxy \
    "$PYTHON_BIN" -m pip install "$@"
}

setup() {
  if [[ -z "$BASE_PYTHON" ]]; then
    for candidate in python3.13 python3.12 python3.11 python3.10 python3; do
      if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c 'import sys; raise SystemExit(sys.version_info < (3, 10))'; then
        BASE_PYTHON="$(command -v "$candidate")"
        break
      fi
    done
  fi
  if [[ -z "$BASE_PYTHON" ]]; then
    echo "MCP requires Python 3.10 or newer." >&2
    return 1
  fi
  if [[ ! -x "$PYTHON_BIN" ]] || ! "$PYTHON_BIN" -c 'import sys; raise SystemExit(sys.version_info < (3, 10))'; then
    "$BASE_PYTHON" -m venv --clear "$VENV_DIR"
  fi
  pip_install --upgrade pip
  pip_install -r "$REQUIREMENTS_FILE"
  echo "MCP runtime installed: $PYTHON_BIN"
}

start() {
  check_runtime
  if [[ -n "$(pids_on_port)" ]]; then
    echo "MCP already running: http://$HOST:$PORT/mcp"
    return
  fi

  mkdir -p "$LOG_DIR"
  if job_exists; then
    launchctl remove "$LABEL" >/dev/null 2>&1 || true
  fi
  launchctl submit -l "$LABEL" -o "$LOG_FILE" -e "$LOG_FILE" -- /bin/zsh -lc \
    "cd '$ROOT_DIR' && INSIGHTMIND_MCP_TRANSPORT=streamable-http INSIGHTMIND_MCP_HOST='$HOST' INSIGHTMIND_MCP_PORT='$PORT' exec '$PYTHON_BIN' -u '$SERVER_FILE'"

  for _ in {1..60}; do
    if [[ -n "$(pids_on_port)" ]]; then
      echo "MCP running: http://$HOST:$PORT/mcp"
      echo "MCP log: $LOG_FILE"
      return
    fi
    sleep 0.5
  done
  echo "MCP failed to listen on port $PORT; inspect $LOG_FILE" >&2
  return 1
}

stop() {
  if job_exists; then
    launchctl remove "$LABEL" >/dev/null 2>&1 || true
  fi
  local pids
  pids="$(pids_on_port)"
  if [[ -n "$pids" ]]; then
    kill $pids 2>/dev/null || true
  fi
  echo "MCP stopped"
}

status() {
  local pids
  pids="$(pids_on_port)"
  if [[ -n "$pids" ]]; then
    echo "MCP running: http://$HOST:$PORT/mcp (pid $pids)"
  else
    echo "MCP stopped: http://$HOST:$PORT/mcp"
  fi
}

stdio() {
  check_runtime
  cd "$ROOT_DIR"
  INSIGHTMIND_MCP_TRANSPORT=stdio exec "$PYTHON_BIN" "$SERVER_FILE"
}

case "${1:-}" in
  setup) setup ;;
  start) start ;;
  stop) stop ;;
  restart) stop; start ;;
  status) status ;;
  stdio) stdio ;;
  *) usage; exit 2 ;;
esac
