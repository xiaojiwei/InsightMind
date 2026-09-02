#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_OUTPUT_DIR="$ROOT_DIR/demo/default/ad/output"
AD_DIR="$ROOT_DIR/apps/ad"
AD_OUTPUT_DIR="$AD_DIR/output"
AD_PYTHON_BIN="${AD_PYTHON:-$AD_DIR/venv/bin/python}"
HR_DEMO_DB="${HR_DEMO_DB:-HRRDB}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
if [[ ! -x "$AD_PYTHON_BIN" ]]; then
  AD_PYTHON_BIN="$(command -v python3 || command -v python)"
fi

if [[ ! -d "$DEMO_OUTPUT_DIR" ]]; then
  echo "Missing demo assets: $DEMO_OUTPUT_DIR" >&2
  exit 1
fi

mkdir -p "$AD_OUTPUT_DIR"
rm -rf "$AD_OUTPUT_DIR/adhoc" "$AD_OUTPUT_DIR/dashboards" "$AD_OUTPUT_DIR/business_kg"
find "$AD_OUTPUT_DIR" -maxdepth 1 -type f -name 'kg_*.ttl' -delete

cp "$DEMO_OUTPUT_DIR/kg_20260901_003.ttl" "$AD_OUTPUT_DIR/kg_20260901_003.ttl"
cp -R "$DEMO_OUTPUT_DIR/adhoc" "$AD_OUTPUT_DIR/adhoc"
cp -R "$DEMO_OUTPUT_DIR/dashboards" "$AD_OUTPUT_DIR/dashboards"
cp -R "$DEMO_OUTPUT_DIR/business_kg" "$AD_OUTPUT_DIR/business_kg"

"$AD_PYTHON_BIN" - "$AD_OUTPUT_DIR" "$HR_DEMO_DB" "$MYSQL_USER" "$MYSQL_PASSWORD" <<'PY'
import json
import re
import sys
from pathlib import Path

from rdflib import Graph

output = Path(sys.argv[1])
database, username, password = sys.argv[2:]
business_path = output / "business_kg" / "indicator-data.ttl"
business_turtle = business_path.read_text(encoding="utf-8")
for predicate, value in (("dbName", database), ("dbUser", username), ("dbPassword", password)):
    replacement = f'ind:{predicate} {json.dumps(value, ensure_ascii=False)}'
    business_turtle, replacements = re.subn(
        rf'ind:{predicate}\s+"(?:[^"\\]|\\.)*"', replacement, business_turtle, count=1
    )
    if replacements != 1:
        raise SystemExit(f"Missing DataConnection property: {predicate}")
business_path.write_text(business_turtle, encoding="utf-8")

for path in (output / "kg_20260901_003.ttl", output / "business_kg" / "indicator-data.ttl"):
    graph = Graph().parse(path, format="turtle")
    if not graph:
        raise SystemExit(f"Demo graph is empty: {path}")

dashboard_ids = sorted(path.stem for path in (output / "dashboards").glob("*.json"))
expected = ["dash_hr_human_capital_panorama", "dash_hr_talent_vitality_pulse"]
if dashboard_ids != expected:
    raise SystemExit(f"Unexpected demo dashboards: {dashboard_ids}")

for dashboard_id in dashboard_ids:
    payload = json.loads((output / "dashboards" / f"{dashboard_id}.json").read_text(encoding="utf-8"))
    if payload.get("id") != dashboard_id:
        raise SystemExit(f"Dashboard ID mismatch: {dashboard_id}")
PY

echo "Demo assets restored:"
echo "  Data KG:      $AD_OUTPUT_DIR/kg_20260901_003.ttl"
echo "  Business KG:  $AD_OUTPUT_DIR/business_kg/indicator-data.ttl"
echo "  Components:   $AD_OUTPUT_DIR/adhoc"
echo "  Dashboards:   $AD_OUTPUT_DIR/dashboards"
