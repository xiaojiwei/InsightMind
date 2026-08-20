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
creates 54 fully synthetic calls for `理想汽车`; no production
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

Usage feedback is enabled by default. Open `http://localhost:8080/feedback` to
query execution, explicit, behavior, and database-metadata feedback. Records are
stored in MySQL; feedback never updates a KG automatically. Successful semantic
queries create a versioned `SemanticQueryPlan`/`ExplainPlan` and a `PENDING`
learning sample. Reviewers can enable, disable, or mark samples stale and can
promote a trace to an evaluation case from the feedback page. Explicit helpful
feedback enables the matching sample, while unhelpful/correction feedback
disables it. Plans, correction steps, memory reviews, and evaluation cases are
linked to the existing `trace_id` and retain the business-KG version used by the
query.

The MySQL store creates `semantic_query_plan`, `semantic_correction_step`,
`semantic_memory`, `semantic_memory_review`, `semantic_eval_case`,
`semantic_eval_run`, and `semantic_eval_result` in addition to the observation
tables. By default the store
uses the first MySQL datasource from `config.local.yaml`/`config.yaml`. Set
`FEEDBACK_ENABLED=false` to disable collection, or use `FEEDBACK_DB_HOST`,
`FEEDBACK_DB_PORT`, `FEEDBACK_DB_USER`, `FEEDBACK_DB_PASSWORD`, and
`FEEDBACK_DB_NAME` to select a dedicated MySQL database.

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

## Semantic Retrieval And Mapping

AD exposes a shared semantic layer for catalog names, governed aliases,
low-cardinality dimension values, fuzzy recall, and optional vector recall.
NLQ, Insight analysis, and the MCP catalog tool use the same process-level,
filesystem-version-aware index.

```text
GET  /api/semantic-retrieval/status                         # anonymous, counts only
GET  /api/semantic-retrieval/search?keyword=销售额&types=measure,dimension
POST /api/semantic-retrieval/map
```

`catalog`, `search`, and `map` are fail-closed. Configure a random
`INSIGHTMIND_SEMANTIC_API_TOKEN` and send it in
`X-InsightMind-Semantic-Token`; configure the same environment variable on the
MCP gateway. All `/api/feedback/*` endpoints similarly require
`INSIGHTMIND_FEEDBACK_API_TOKEN` in `X-InsightMind-Feedback-Token`.
The feedback management page asks an administrator to enter this token and
keeps it in browser `sessionStorage`; the secret is never embedded in the
served HTML. For multi-user production deployments, place these endpoints
behind the existing authenticated gateway/RBAC layer rather than distributing
the management token to ordinary users.

Maintain reviewed file aliases in `semantic_dictionary.yaml`. The existing
`synonyms.yaml` is imported as a lower-priority legacy source-schema synonym
list. Feedback-derived entries follow a PENDING → ENABLED review workflow via
`/api/feedback/dictionary`; online retrieval never promotes a user correction
automatically. Dimension values are indexed only when they come from explicit
dictionary entries or safe KG samples, pass cardinality/PII checks, and the
dimension is explicitly governed as `PUBLIC_ENUM`. `INTERNAL_ENUM`, `PII`, and
unknown dimensions are deny-by-default. A BKG build stores a hash-checked
source-KG sidecar; missing or mismatched bindings disable source value samples
instead of selecting the newest unrelated graph.
For dimensions attached to more than one fact table, reviewed executable values
must declare their `tables`; a scoped `valuePolicies.requiredTables` guard
prevents a demo/domain dictionary from attaching to another BKG that happens to
reuse a generic dimension code.

Run the deterministic quality gate after changing the KG or dictionary:

```bash
PYTHONPATH=. python scripts/evaluate_semantic_retrieval.py
# Add --with-vector to evaluate the configured vector provider as well.
# With DA running, require one real NLQ -> DA value-filter execution:
PYTHONPATH=. python scripts/evaluate_semantic_retrieval.py \
  --da-url http://127.0.0.1:8091/bi/v1/datasource/query \
  --require-execution
```

The report separates positive and negative value cases. Missing positive,
negative, or (under `--with-vector`) vector-required coverage makes the quality
gate fail instead of producing a misleading green result.

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
