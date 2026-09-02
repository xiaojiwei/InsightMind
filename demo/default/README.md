# InsightMind Default Demo

This directory contains the checked-in HR demo assets restored by
`scripts/init-demo-assets.sh` and auto-seeded by AD when runtime output assets
are missing.

## Contents

- `ad/output/kg_20260901_003.ttl` — current HR data-source knowledge graph.
- `ad/output/business_kg/indicator-data.ttl` — current HR business knowledge graph.
- `ad/output/adhoc/hr_*.json` — saved HR Ad-Hoc components.
- `ad/output/dashboards/dash_hr_*.json` — saved HR dashboard definitions.

The database schema and deterministic data generator live in the application
tree so they can also be used directly during development:

- `apps/da/schema.sql`

To initialize a fresh local demo:

```bash
./scripts/init-demo-assets.sh
./scripts/init-demo-db.sh
./scripts/insightmind.sh restart
```

The initializer restores the current HR graph files and saved HR dashboard
assets used by the active demo.

After both services start, open the ready-to-use dashboards:

- `/dashboard/view/dash_hr_human_capital_panorama`
- `/dashboard/view/dash_hr_talent_vitality_pulse`
