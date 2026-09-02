#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
if [[ -z "$MYSQL_BIN" && -x "/opt/homebrew/opt/mysql-client/bin/mysql" ]]; then
  MYSQL_BIN="/opt/homebrew/opt/mysql-client/bin/mysql"
fi
if [[ -z "$MYSQL_BIN" ]]; then
  echo "MySQL client not found. Set MYSQL_BIN to the mysql executable." >&2
  exit 1
fi

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"

HR_DEMO_DB="${HR_DEMO_DB:-HRRDB}"
DA_DB="${DA_DB:-indbtest}"

if [[ ! "$HR_DEMO_DB" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "HR_DEMO_DB must contain only letters, digits, and underscores." >&2
  exit 2
fi
if [[ ! "$DA_DB" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "DA_DB must contain only letters, digits, and underscores." >&2
  exit 2
fi

AD_PYTHON_BIN="${AD_PYTHON:-$ROOT_DIR/apps/ad/venv/bin/python}"
if [[ ! -x "$AD_PYTHON_BIN" ]]; then
  AD_PYTHON_BIN="$(command -v python3 || command -v python)"
fi

LOCAL_CONFIG="$ROOT_DIR/apps/ad/config.local.yaml"
if [[ -f "$LOCAL_CONFIG" ]]; then
  configured_database="$(
    "$AD_PYTHON_BIN" - "$LOCAL_CONFIG" <<'PY'
import sys
import yaml

payload = yaml.safe_load(open(sys.argv[1], encoding="utf-8")) or {}
source = next((item for item in payload.get("datasources", []) if item.get("type") == "mysql"), {})
print(source.get("database") or "")
PY
  )"
  configured_database_lower="$(printf '%s' "$configured_database" | tr '[:upper:]' '[:lower:]')"
  hr_demo_database_lower="$(printf '%s' "$HR_DEMO_DB" | tr '[:upper:]' '[:lower:]')"
  if [[ -n "$configured_database" && "$configured_database_lower" != "$hr_demo_database_lower" ]]; then
    echo "Warning: $LOCAL_CONFIG targets $configured_database, not $HR_DEMO_DB." >&2
    echo "         It was not changed. Update it before starting the HR demo." >&2
  fi
fi

mysql_args=(
  "-h${MYSQL_HOST}"
  "-P${MYSQL_PORT}"
  "-u${MYSQL_USER}"
)

run_mysql() {
  MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_BIN" "${mysql_args[@]}" "$@"
}

echo "Recreating synthetic HR demo database: $HR_DEMO_DB"
run_mysql -e "DROP DATABASE IF EXISTS \`${HR_DEMO_DB}\`; CREATE DATABASE \`${HR_DEMO_DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "Generating deterministic HR sample data"
(
  "$AD_PYTHON_BIN" "$ROOT_DIR/apps/ad/demo_hr_data.py" \
    --host "$MYSQL_HOST" \
    --port "$MYSQL_PORT" \
    --user "$MYSQL_USER" \
    --password "$MYSQL_PASSWORD" \
    --database "$HR_DEMO_DB"
)

echo "Installing HR analytics views"
sed "s/HRRDB/${HR_DEMO_DB}/g" "$ROOT_DIR/apps/ad/hr_analytics_views.sql" | run_mysql

echo "Initializing empty HR alert store"
(
  cd "$ROOT_DIR/apps/ad"
  ALERT_DB_HOST="$MYSQL_HOST" \
  ALERT_DB_PORT="$MYSQL_PORT" \
  ALERT_DB_USER="$MYSQL_USER" \
  ALERT_DB_PASSWORD="$MYSQL_PASSWORD" \
  ALERT_DB_NAME="$HR_DEMO_DB" \
  PYTHONPATH=. \
    "$AD_PYTHON_BIN" -c "from kg_builder.alerts.models import init_db; init_db()"
)

echo "Creating DA metadata database: $DA_DB"
run_mysql -e "CREATE DATABASE IF NOT EXISTS \`${DA_DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
run_mysql "$DA_DB" < "$ROOT_DIR/apps/da/schema.sql"

echo "Demo databases are ready:"
echo "  HR business data:     $HR_DEMO_DB (107 fully synthetic employees)"
echo "  DA metadata:          $DA_DB"
