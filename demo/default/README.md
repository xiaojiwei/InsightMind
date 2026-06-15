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
- `apps/da/schema.sql`

To initialize a fresh local demo:

```bash
./scripts/init-demo-assets.sh
./scripts/init-demo-db.sh
./scripts/insightmind.sh restart
```
