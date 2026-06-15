#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSQL_BIN="${MYSQL_BIN:-mysql}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"

TPCDS_DB="${TPCDS_DB:-tpcds}"
DA_DB="${DA_DB:-indbtest}"

mysql_args=(
  "-h${MYSQL_HOST}"
  "-P${MYSQL_PORT}"
  "-u${MYSQL_USER}"
)

run_mysql() {
  MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_BIN" "${mysql_args[@]}" "$@"
}

echo "Recreating demo business database: $TPCDS_DB"
run_mysql -e "DROP DATABASE IF EXISTS \`${TPCDS_DB}\`; CREATE DATABASE \`${TPCDS_DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sed "s/^USE tpcds;$/USE ${TPCDS_DB};/" "$ROOT_DIR/apps/ad/tpcds_schema.sql" | run_mysql

echo "Generating deterministic TPC-DS sample data"
(
  cd "$ROOT_DIR/apps/ad"
  AD_PYTHON_BIN="${AD_PYTHON:-./venv/bin/python}"
  if [[ ! -x "$AD_PYTHON_BIN" ]]; then
    AD_PYTHON_BIN="$(command -v python3 || command -v python)"
  fi

  TPCDS_DB_HOST="$MYSQL_HOST" \
  TPCDS_DB_PORT="$MYSQL_PORT" \
  TPCDS_DB_USER="$MYSQL_USER" \
  TPCDS_DB_PASSWORD="$MYSQL_PASSWORD" \
  TPCDS_DB_NAME="$TPCDS_DB" \
    "$AD_PYTHON_BIN" tpcds_data.py
)

echo "Creating DA metadata database: $DA_DB"
run_mysql -e "CREATE DATABASE IF NOT EXISTS \`${DA_DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
run_mysql "$DA_DB" < "$ROOT_DIR/apps/da/schema.sql"

echo "Demo databases are ready:"
echo "  TPC-DS business data: $TPCDS_DB"
echo "  DA metadata:          $DA_DB"
