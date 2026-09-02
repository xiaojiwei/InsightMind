# InsightMind Default Demo

This directory contains the checked-in HR demo assets restored by
`scripts/init-demo-assets.sh` and auto-seeded by AD when runtime output assets
are missing.

## Contents

- `ad/output/kg_20260901_003.ttl` — current HR data-source knowledge graph.
- `ad/output/business_kg/indicator-data.ttl` — current HR business knowledge graph.
- `ad/output/adhoc/hr_*.json` — saved HR Ad-Hoc components.
- `ad/output/dashboards/dash_hr_*.json` — saved HR dashboard definitions.

The database schema and deterministic HR data generator live in the application
tree so they can also be used directly during development:

- `apps/ad/demo_hr_data.py`
- `apps/ad/hr_analytics_views.sql`
- `apps/da/schema.sql`

To initialize a fresh local demo:

```bash
./scripts/insightmind.sh setup full
(cd apps/da && mvn -DskipTests package)
./scripts/init-demo-db.sh
./scripts/init-demo-assets.sh
./scripts/insightmind.sh restart
./scripts/verify-hr-demo.sh
```

The initializer restores the current HR graph files and saved HR dashboard
assets used by the active demo.

After both services start, open the ready-to-use dashboards:

- `/dashboard/view/dash_hr_human_capital_panorama`
- `/dashboard/view/dash_hr_talent_vitality_pulse`
