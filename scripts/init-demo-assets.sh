#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_OUTPUT_DIR="$ROOT_DIR/demo/default/ad/output"
AD_DIR="$ROOT_DIR/apps/ad"
AD_OUTPUT_DIR="$AD_DIR/output"
DA_RESOURCE_TTL="$ROOT_DIR/apps/da/src/main/resources/indicator-data.ttl"

if [[ ! -d "$DEMO_OUTPUT_DIR" ]]; then
  echo "Missing demo assets: $DEMO_OUTPUT_DIR" >&2
  exit 1
fi

mkdir -p "$AD_OUTPUT_DIR"
cp -R "$DEMO_OUTPUT_DIR"/. "$AD_OUTPUT_DIR"/
cp "$DEMO_OUTPUT_DIR/business_kg/indicator-data.ttl" "$DA_RESOURCE_TTL"

# The bundled call-quality database contains im_call_quality_fact only.  Drop
# stale CRM/funnel measures left in historical exports so validation, NLQ and
# the running data-agent never advertise non-executable metrics.
"$AD_DIR/venv/bin/python" "$AD_DIR/sanitize_demo_business_kg.py" \
  "$AD_OUTPUT_DIR/output/business_kg/indicator-data.ttl" \
  --allow-table im_call_quality_fact

echo "Demo assets restored:"
echo "  Data KG:      $AD_OUTPUT_DIR/kg_tpcds.ttl"
echo "  Business KG:  $AD_OUTPUT_DIR/business_kg/indicator-data.ttl"
echo "  Components:   $AD_OUTPUT_DIR/adhoc"
echo "  Dashboards:   $AD_OUTPUT_DIR/dashboards"
