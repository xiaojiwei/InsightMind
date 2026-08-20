# InsightMind Default Demo

This directory contains the checked-in demo assets restored by
`scripts/init-demo-assets.sh` and auto-seeded by AD when `apps/ad/output` is
empty or missing.

## Contents

- `ad/output/kg_tpcds.ttl` — default TPC-DS data-source knowledge graph.
- `ad/output/business_kg/indicator-data.ttl` — default business knowledge graph.
- `ad/output/adhoc/*.json` — saved Ad-Hoc components.
- `ad/output/dashboards/*.json` — saved dashboard definitions.

The database schema and deterministic data generator live in the application
tree so they can also be used directly during development:

- `apps/ad/tpcds_schema.sql`
- `apps/ad/tpcds_data.py`
- `apps/ad/demo_call_sop_data.py` — 54 fully synthetic call-quality records.
- `apps/ad/demo_celn_data.py` — synthetic CELN funnel and drill-down records for Smart Insight.
- `apps/da/schema.sql`

To initialize a fresh local demo:

```bash
./scripts/init-demo-assets.sh
./scripts/init-demo-db.sh
./scripts/insightmind.sh restart
```

The initializer recreates the local `tpcds` and `da_tms` demo databases, loads
the DA metadata database, and installs the checked-in pivot alert rules. The
call transcripts, CELN customers, stage evidence, employees, store and
conversion events are synthetic and use the fictional demo scope
`理想汽车`.

After both services start, open the three ready-to-use dashboards:

- `/dashboard/view/dash_da_tms_call_sop_diagnosis`
- `/dashboard/view/dash_da_tms_call_sop_workbench`
- `/dashboard/view/dash_da_tms_call_monitor_alert`
