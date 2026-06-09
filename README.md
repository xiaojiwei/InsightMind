# InsightMind

InsightMind combines the AD knowledge-graph builder and the DA indicator service
into one local analytics intelligence workspace.

The merged project contains:

- `apps/ad`: Python/FastAPI knowledge graph builder, Ad-Hoc UI, dashboard
  preview, NLQ, statistical analysis, and DA-compatible semantic APIs.
- `apps/da`: Spring Boot indicator service for indicator metadata, dimensional
  SQL generation, datasource query APIs, dashboards, and monitoring.
- `scripts/insightmind.sh`: one command to start, stop, restart, and inspect
  both services.

## Local Services

```text
AD: http://localhost:8080/
DA: http://localhost:8091/
```

AD reads and writes graph artifacts under `apps/ad/output/`. DA is configured to
load the business KG from:

```text
apps/ad/output/business_kg/indicator-data.ttl
```

## Start, Stop, Restart

From the project root:

```bash
./scripts/insightmind.sh start
./scripts/insightmind.sh stop
./scripts/insightmind.sh restart
./scripts/insightmind.sh status
```

Manage only one service:

```bash
./scripts/insightmind.sh restart ad
./scripts/insightmind.sh restart da
```

Logs are written to:

```text
logs/ad.log
logs/da.log
```

## Install AD Dependencies

```bash
cd apps/ad
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Run AD manually:

```bash
cd apps/ad
source venv/bin/activate
python web_app.py
```

## Build DA

```bash
cd apps/da
mvn -DskipTests package
```

The service script uses this jar when it exists:

```text
apps/da/target/da-indicator-0.0.1-SNAPSHOT.jar
```

If the jar is missing, run the Maven build first.

## Database And Configuration

AD uses `apps/ad/config.yaml` as the default example datasource config. For real
local credentials, create an untracked local config and keep secrets out of git:

```bash
cp apps/ad/config.yaml apps/ad/config.local.yaml
```

DA uses the `dev` profile by default. Prepare the local DA metadata schema with:

```bash
mysql -u YOUR_DB_USER -p -e \
  "CREATE DATABASE IF NOT EXISTS indbtest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

mysql -u YOUR_DB_USER -p indbtest < apps/da/schema.sql
```

Prefer runtime overrides or local-only config files for real datasource
passwords.

## Run Tests

AD:

```bash
cd apps/ad
source venv/bin/activate
PYTHONPATH=. pytest
```

DA:

```bash
cd apps/da
mvn test
```

## Project Layout

```text
InsightMind/
  apps/
    ad/
    da/
  logs/
  scripts/
    insightmind.sh
```

