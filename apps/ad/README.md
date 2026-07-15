# GraphBuilder

GraphBuilder builds data-source and business knowledge graphs from relational
database metadata, then exposes them through a FastAPI web UI for exploration,
Ad-Hoc query, dashboard preview, NLQ, statistical analysis, and business KG
generation.

The current workflow is usually:

1. Connect to a relational database such as MySQL, SQL Server, Oracle, or SQLite.
2. Parse tables, columns, comments, samples, and explicit/implicit relationships.
3. Generate a data-source KG in Turtle/JSON-LD under `output/`.
4. Generate or import an indicator/business KG under `output/business_kg/`.
5. Query the business KG through the DA-compatible Ad-Hoc APIs and the web UI.

## Main Features

- Data-source KG construction from database schema and optional samples.
- Relationship discovery from primary keys, foreign keys, comments, and semantic similarity.
- SPARQL-style graph inspection and graph export.
- Business KG generation by deterministic ETL from an indicator platform database, or by an OpenAI-compatible LLM when no reference indicator platform is configured.
- Ad-Hoc query, drill path, filters, dashboard preview, NLQ, and statistical analysis in the web UI.
- DA-compatible semantic metadata and query endpoints used by the indicator service.

## Requirements

- Python 3.9 or newer.
- A reachable database for the graph-building target. The checked-in
  `config.yaml` is only an example and uses local MySQL defaults.
- Optional: an OpenAI-compatible LLM gateway for translation, NLQ fallback,
  or LLM business KG generation.

For the default call-quality demo used in local development, the repository
includes checked-in assets under `../../demo/default/ad/output`. AD restores
them into `output/` automatically when they are missing. The database bootstrap
creates 54 fully synthetic calls for `小鹏汽车杭州演示体验中心`; no production
customer, employee, transcript or store data is included.

## Install

```bash
cd /path/to/GraphBuilder
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

`requirements.txt` is the fast deployment profile and only installs the core
runtime. Add optional profiles when those features are needed:

```bash
pip install -r requirements-analysis.txt   # statistical analysis, full Insight modeling, embeddings
pip install -r requirements-db-extra.txt   # SQL Server, Oracle, MySQL-compatible protocol
pip install -r requirements-dev.txt        # tests and local checks
pip install -r requirements-full.txt       # all AD dependencies
```

`sentence-transformers` is only installed by the analysis/full profiles. If it
needs to download a model, keep network access available for the first run, or
disable implicit relation discovery in `config.yaml`.

## Configure

Create a local config from the example and change database credentials for your
machine:

```bash
cp config.yaml config.local.yaml
```

Edit `config.local.yaml`:

```yaml
datasources:
  - name: mysql_local
    type: mysql
    host: localhost
    port: 3306
    database: tpcds
    schema: tpcds
    username: YOUR_DB_USER
    password: YOUR_DB_PASSWORD
```

Do not commit real datasource passwords or LLM keys. Put LLM settings in your
shell or in an untracked `.env` file:

```bash
export LLM_BASE_URL="https://your-openai-compatible-gateway"
export LLM_API_KEY="YOUR_API_KEY"
export LLM_MODEL_NAME="YOUR_MODEL_NAME"
```

LLM configuration is optional for basic KG build and deterministic ETL paths.

## Run The Web UI

For local AD/DA development, use the service management script from this
repository:

```bash
cd /path/to/GraphBuilder
./manage_ad_da.sh start      # start AD and DA
./manage_ad_da.sh restart    # restart AD and DA
./manage_ad_da.sh stop       # stop AD and DA
./manage_ad_da.sh status     # show current service status
```

You can also manage one service at a time:

```bash
./manage_ad_da.sh restart ad
./manage_ad_da.sh restart da
```

Local service URLs:

```text
AD: http://localhost:8080/
DA: http://localhost:8091/
```

Logs are written to `ad.log` for AD and `/private/tmp/da-indicator.log` for DA.

To run only the GraphBuilder web UI manually:

```bash
cd /path/to/GraphBuilder
source venv/bin/activate
python web_app.py
```

Open:

```text
http://localhost:8080/
```

The web UI can build the data-source KG, generate/load a business KG, and run
Ad-Hoc queries. Generated artifacts are written under `output/`.

## Default Demo Assets

From the repository root:

```bash
./scripts/init-demo-assets.sh
./scripts/init-demo-db.sh
./scripts/insightmind.sh restart
```

This restores the data-source KG, call-quality business KG, saved Ad-Hoc
components, three dashboards, synthetic call records and pivot alert rules.
Open:

```text
http://localhost:8080/dashboard/view/dash_da_tms_call_sop_diagnosis
http://localhost:8080/dashboard/view/dash_da_tms_call_sop_workbench
http://localhost:8080/dashboard/view/dash_da_tms_call_monitor_alert
```

## Run From CLI

Build a KG:

```bash
source venv/bin/activate
python main.py build --config config.local.yaml --output output/kg.ttl
```

Inspect foreign-key relationships:

```bash
python main.py query --graph output/kg.ttl --fk-graph
```

Query a table schema:

```bash
python main.py query --graph output/kg.ttl --table-schema web_sales
```

Export to JSON-LD:

```bash
python main.py export --graph output/kg.ttl --format jsonld --output output/kg.jsonld
```

## Run Tests

```bash
source venv/bin/activate
PYTHONPATH=. pytest
```

For a focused Ad-Hoc/public-date check:

```bash
PYTHONPATH=. pytest \
  tests/test_ad_semantic_api.py \
  tests/test_pivot_public_dimensions.py \
  tests/test_public_date_dimensions.py
```

## DA Integration

DA reads the business KG from:

```text
output/business_kg/indicator-data.ttl
```

When running DA locally, point its `indicator.graph.data-path` to this file.
Start GraphBuilder first when you want to regenerate the business KG, then
restart DA or reload DA metadata as needed.

## OpenClaw Or LLM Agent Runbook

Use this prompt for an agent such as OpenClaw:

```text
Work in the GraphBuilder repository.
Do not read, print, commit, or overwrite real credentials.
If a database password or LLM API key is needed, stop and ask the user to set it
as an environment variable.

Install:
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

Install optional profiles only when needed:
pip install -r requirements-analysis.txt
pip install -r requirements-db-extra.txt
pip install -r requirements-dev.txt

Configure:
cp config.yaml config.local.yaml
Edit config.local.yaml with the local database host, database, username, and
password supplied by the user. Do not commit config.local.yaml if it contains
secrets.

Run:
source venv/bin/activate
python web_app.py

Verify:
Open http://localhost:8080/
Run PYTHONPATH=. pytest before committing code changes.
```

## Can A Fresh Clone Start?

A fresh clone can install dependencies, initialize the deterministic demo
database with `scripts/init-demo-db.sh`, restore the checked-in assets, and open
all three call-quality dashboards. These dashboard paths do not require an LLM.
Other LLM-dependent features still require user-provided environment variables.
