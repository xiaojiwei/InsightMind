#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AD_DIR="$ROOT_DIR/apps/ad"
AD_OUTPUT_DIR="$AD_DIR/output"
AD_PYTHON_BIN="${AD_PYTHON:-$AD_DIR/venv/bin/python}"
AD_PORT="${INSIGHTMIND_AD_PORT:-8080}"
DA_PORT="${INSIGHTMIND_DA_PORT:-8091}"

if [[ ! -x "$AD_PYTHON_BIN" ]]; then
  AD_PYTHON_BIN="$(command -v python3 || command -v python)"
fi

"$AD_PYTHON_BIN" - "$AD_OUTPUT_DIR" <<'PY'
import sys
from pathlib import Path

from rdflib import Graph

output = Path(sys.argv[1])
for path in (
    output / "kg_20260901_003.ttl",
    output / "business_kg" / "indicator-data.ttl",
):
    graph = Graph().parse(path, format="turtle")
    if not graph:
        raise SystemExit(f"Demo graph is empty: {path}")

expected = ["dash_hr_human_capital_panorama", "dash_hr_talent_vitality_pulse"]
dashboards = sorted(path.stem for path in (output / "dashboards").glob("*.json"))
if dashboards != expected:
    raise SystemExit(f"Unexpected demo dashboards: {dashboards}")
print("HR demo files verified: two graphs and two dashboards.")
PY

if [[ "${VERIFY_RUNNING_SERVICES:-0}" != "1" ]]; then
  exit 0
fi

dashboard_json="$(curl --fail --silent "http://127.0.0.1:${AD_PORT}/api/dashboard/v1/list")"
"$AD_PYTHON_BIN" - "$dashboard_json" <<'PY'
import json
import sys

expected = ["dash_hr_human_capital_panorama", "dash_hr_talent_vitality_pulse"]
payload = json.loads(sys.argv[1])
actual = sorted(item["id"] for item in payload.get("items", []))
if actual != expected:
    raise SystemExit(f"Unexpected active dashboards: {actual}")
PY

for dashboard_id in dash_hr_human_capital_panorama dash_hr_talent_vitality_pulse; do
  curl --fail --silent "http://127.0.0.1:${AD_PORT}/dashboard/view/${dashboard_id}" >/dev/null
done
curl --fail --silent "http://127.0.0.1:${DA_PORT}/api/graph/reasoning/measure/MEAS_workforce_headcount/compatible-dimensions" >/dev/null
echo "Running services verified: both HR dashboards and the business graph are active."
