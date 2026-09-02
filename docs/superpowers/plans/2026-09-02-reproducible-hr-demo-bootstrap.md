# Reproducible HR Demo Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A fresh InsightMind checkout can initialize a synthetic HRRDB database, activate the checked-in HR graphs, and display exactly the two HR demonstration dashboards.

**Architecture:** Keep the existing AD-to-DA graph pipeline. A deterministic Python seed module supplies the source HR schema and data; existing HR views and checked-in Turtle/dashboard assets are installed by corrected shell scripts. Static tests validate script and asset contracts, while an explicit smoke script validates a real local MySQL and running services.

**Tech Stack:** Python 3, PyMySQL, MySQL 8-compatible SQL, Bash, pytest, rdflib, FastAPI, Spring Boot/Jena.

**Spec:** `docs/superpowers/specs/2026-09-02-reproducible-hr-demo-design.md`

## Global Constraints

- Do not introduce a service, sub-system, or runtime dependency.
- Do not commit real credentials, LLM keys, or business data.
- Keep Java common dependencies; do not reintroduce removed drill-down-specific dependencies.
- Only manipulate explicitly named HR demo databases and demo output directories.
- Never overwrite `apps/ad/config.local.yaml`.
- All HR demo data is deterministic and fully synthetic.

---

### Task 1: Deterministic HRRDB source seed

**Files:**
- Create: `apps/ad/demo_hr_data.py`
- Create: `apps/ad/tests/test_demo_hr_data.py`

**Interfaces:**
- Produces `build_demo_rows() -> dict[str, list[tuple]]` with keys `regions`, `countries`, `locations`, `departments`, `jobs`, `employees`, and `job_history`.
- Produces `seed_database(*, host: str, port: int, user: str, password: str, database: str) -> None`.
- CLI accepts `--host`, `--port`, `--user`, `--password`, and `--database`.

- [ ] **Step 1: Write the failing data-contract tests**

```python
from demo_hr_data import build_demo_rows


def test_build_demo_rows_is_deterministic_and_complete():
    first = build_demo_rows()
    assert first == build_demo_rows()
    assert set(first) == {
        "regions", "countries", "locations", "departments",
        "jobs", "employees", "job_history",
    }
    assert len(first["employees"]) == 107
    assert len(first["job_history"]) >= 10


def test_employees_reference_known_departments_jobs_and_managers():
    rows = build_demo_rows()
    department_ids = {row[0] for row in rows["departments"]}
    job_ids = {row[0] for row in rows["jobs"]}
    employee_ids = {row[0] for row in rows["employees"]}
    for employee in rows["employees"]:
        assert employee[7] in job_ids
        assert employee[9] in department_ids
        assert employee[8] is None or employee[8] in employee_ids
```

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `cd apps/ad && PYTHONPATH=. pytest tests/test_demo_hr_data.py -q`

Expected: collection failure because `demo_hr_data` does not exist.

- [ ] **Step 3: Implement the minimal deterministic seed module**

Create MySQL-compatible DDL for the standard HR tables, fixed synthetic lookup data, and 107 predictable employees. Use one stable tuple layout per table and parameterized `executemany` inserts. Reject unsafe database identifiers before issuing `USE` or DDL:

```python
DATABASE_NAME = re.compile(r"^[A-Za-z0-9_]+$")


def validate_database_name(database: str) -> str:
    if not DATABASE_NAME.fullmatch(database):
        raise ValueError("database must contain only letters, digits, and underscores")
    return database


def seed_database(*, host: str, port: int, user: str, password: str, database: str) -> None:
    database = validate_database_name(database)
    with pymysql.connect(host=host, port=port, user=user, password=password, database=database) as connection:
        with connection.cursor() as cursor:
            for statement in DDL_STATEMENTS:
                cursor.execute(statement)
            for table_name, rows in build_demo_rows().items():
                cursor.executemany(INSERT_SQL[table_name], rows)
        connection.commit()
```

- [ ] **Step 4: Run focused tests**

Run: `cd apps/ad && PYTHONPATH=. pytest tests/test_demo_hr_data.py -q`

Expected: PASS.

- [ ] **Step 5: Commit the seed contract**

```bash
git add apps/ad/demo_hr_data.py apps/ad/tests/test_demo_hr_data.py
git commit -m "feat(demo): seed deterministic HR database"
```

### Task 2: HR-only database bootstrap and default datasource

**Files:**
- Modify: `scripts/init-demo-db.sh`
- Modify: `apps/ad/config.yaml`
- Create: `apps/ad/tests/test_hr_demo_bootstrap.py`

**Interfaces:**
- `scripts/init-demo-db.sh` accepts existing `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DA_DB`, plus `HR_DEMO_DB` defaulting to `HRRDB`.
- It invokes `apps/ad/demo_hr_data.py` and applies `apps/ad/hr_analytics_views.sql` after selecting `HR_DEMO_DB`.
- `apps/ad/config.yaml` first MySQL datasource has `database: "HRRDB"` and `schema: "HRRDB"`.

- [ ] **Step 1: Write failing bootstrap contract tests**

```python
def test_default_ad_datasource_targets_hr_demo():
    config = yaml.safe_load((AD_DIR / "config.yaml").read_text(encoding="utf-8"))
    datasource = config["datasources"][0]
    assert datasource["database"] == "HRRDB"
    assert datasource["schema"] == "HRRDB"


def test_database_bootstrap_uses_only_hr_seed_and_views():
    script = (ROOT_DIR / "scripts/init-demo-db.sh").read_text(encoding="utf-8")
    assert 'HR_DEMO_DB="${HR_DEMO_DB:-HRRDB}"' in script
    assert "demo_hr_data.py" in script
    assert "hr_analytics_views.sql" in script
    assert "tpcds_data.py" not in script
    assert "demo_call_sop_data.py" not in script
    assert "demo_celn_data.py" not in script
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `cd apps/ad && PYTHONPATH=. pytest tests/test_hr_demo_bootstrap.py -q`

Expected: FAIL because config and script still select TPCDS and old data generators.

- [ ] **Step 3: Replace old DB initialization calls**

Update `init-demo-db.sh` to create only the configured HR demo database, invoke the new seed module, and apply the HR views through a database-name substitution that works for a configurable HR database:

```bash
HR_DEMO_DB="${HR_DEMO_DB:-HRRDB}"

echo "Recreating synthetic HR demo database: $HR_DEMO_DB"
run_mysql -e "DROP DATABASE IF EXISTS \`${HR_DEMO_DB}\`; CREATE DATABASE \`${HR_DEMO_DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"$AD_PYTHON_BIN" "$ROOT_DIR/apps/ad/demo_hr_data.py" --host "$MYSQL_HOST" --port "$MYSQL_PORT" --user "$MYSQL_USER" --password "$MYSQL_PASSWORD" --database "$HR_DEMO_DB"
sed "s/^USE HRRDB;$/USE ${HR_DEMO_DB};/" "$ROOT_DIR/apps/ad/hr_analytics_views.sql" | run_mysql
```

Keep creation of `DA_DB` and application of `apps/da/schema.sql`; remove alert-rule, TPCDS, CELN, and call-quality setup.

- [ ] **Step 4: Update default AD datasource and run focused checks**

Set `database` and `schema` in `apps/ad/config.yaml` to `HRRDB`. Run:

```bash
bash -n scripts/init-demo-db.sh
cd apps/ad && PYTHONPATH=. pytest tests/test_demo_hr_data.py tests/test_hr_demo_bootstrap.py -q
```

Expected: shell syntax succeeds and all focused tests pass.

- [ ] **Step 5: Commit bootstrap configuration**

```bash
git add scripts/init-demo-db.sh apps/ad/config.yaml apps/ad/tests/test_hr_demo_bootstrap.py
git commit -m "fix(demo): bootstrap HR database by default"
```

### Task 3: Correct HR asset activation

**Files:**
- Modify: `scripts/init-demo-assets.sh`
- Modify: `apps/ad/web_app.py`
- Modify: `apps/ad/tests/test_hr_demo_bootstrap.py`

**Interfaces:**
- `scripts/init-demo-assets.sh` restores the checked-in HR output files and validates them with the AD Python runtime.
- `web_app.py:_seed_demo_assets()` remains non-destructive and only fills absent assets.
- The installed demo output contains exactly `dash_hr_human_capital_panorama.json` and `dash_hr_talent_vitality_pulse.json` in `dashboards/`.

- [ ] **Step 1: Extend failing asset contract tests**

```python
def test_asset_bootstrap_uses_hr_assets_without_legacy_sanitizer():
    script = (ROOT_DIR / "scripts/init-demo-assets.sh").read_text(encoding="utf-8")
    assert '"$AD_OUTPUT_DIR/business_kg/indicator-data.ttl"' in script
    assert "sanitize_demo_business_kg.py" not in script
    assert "kg_20260901_003.ttl" in script


def test_checked_in_hr_assets_are_parseable_and_have_two_dashboards():
    graph = Graph().parse(DEMO_OUTPUT / "kg_20260901_003.ttl", format="turtle")
    business_graph = Graph().parse(DEMO_OUTPUT / "business_kg/indicator-data.ttl", format="turtle")
    assert len(graph) > 0
    assert len(business_graph) > 0
    assert sorted(path.stem for path in (DEMO_OUTPUT / "dashboards").glob("*.json")) == [
        "dash_hr_human_capital_panorama", "dash_hr_talent_vitality_pulse",
    ]
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `cd apps/ad && PYTHONPATH=. pytest tests/test_hr_demo_bootstrap.py -q`

Expected: FAIL because the script contains the legacy sanitizer and prints the old graph name.

- [ ] **Step 3: Correct the asset installer**

Remove the sanitizer invocation entirely. Copy the data graph, business graph, Ad-Hoc JSON, and dashboard JSON from `demo/default/ad/output` to `apps/ad/output`; then copy the business graph into DA resources. Add a small Python validation block that parses both TTL files with `rdflib.Graph().parse(..., format="turtle")` and asserts the exact two dashboard JSON stems. Print the HR graph filename rather than `kg_tpcds.ttl`.

- [ ] **Step 4: Preserve non-destructive start-up seeding and run checks**

Inspect the `web_app.py` change narrowly: it must not delete runtime assets and must continue to restore only missing files. Run:

```bash
bash -n scripts/init-demo-assets.sh
cd apps/ad && PYTHONPATH=. pytest tests/test_hr_demo_bootstrap.py -q
```

Expected: PASS.

- [ ] **Step 5: Commit asset activation**

```bash
git add scripts/init-demo-assets.sh apps/ad/web_app.py apps/ad/tests/test_hr_demo_bootstrap.py apps/da/src/main/resources/indicator-data.ttl
git commit -m "fix(demo): activate checked-in HR graph assets"
```

### Task 4: Documentation and clean-environment verifier

**Files:**
- Modify: `README.md`
- Modify: `apps/ad/README.md`
- Modify: `demo/default/README.md`
- Create: `scripts/verify-hr-demo.sh`
- Modify: `apps/ad/tests/test_hr_demo_bootstrap.py`

**Interfaces:**
- `scripts/verify-hr-demo.sh` checks the two dashboard files, parses both graph files, and optionally calls local AD/DA endpoints when `VERIFY_RUNNING_SERVICES=1`.
- Documentation uses `HR_DEMO_DB=HRRDB` and describes the two HR dashboard IDs.

- [ ] **Step 1: Write failing docs/verifier tests**

```python
def test_documentation_and_verifier_name_the_two_hr_dashboards():
    docs = "\n".join(path.read_text(encoding="utf-8") for path in README_FILES)
    assert "dash_hr_human_capital_panorama" in docs
    assert "dash_hr_talent_vitality_pulse" in docs
    assert "three call-quality dashboards" not in docs
    verifier = (ROOT_DIR / "scripts/verify-hr-demo.sh").read_text(encoding="utf-8")
    assert "dash_hr_human_capital_panorama" in verifier
    assert "dash_hr_talent_vitality_pulse" in verifier
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `cd apps/ad && PYTHONPATH=. pytest tests/test_hr_demo_bootstrap.py -q`

Expected: FAIL because the current documentation describes call-quality dashboards and no HR verifier exists.

- [ ] **Step 3: Add a runnable verifier and update docs**

The verifier must use `set -euo pipefail`, resolve `ROOT_DIR`, execute the same rdflib validation contract as the asset script, and when `VERIFY_RUNNING_SERVICES=1` use `curl --fail` to check AD and DA health plus the dashboard list endpoint. Documentation must give this clean setup sequence:

```bash
cd InsightMind
./scripts/init-demo-db.sh
./scripts/init-demo-assets.sh
cd apps/da && mvn -DskipTests package
cd ../..
./scripts/insightmind.sh start
./scripts/verify-hr-demo.sh
```

- [ ] **Step 4: Run static verification**

Run:

```bash
bash -n scripts/verify-hr-demo.sh
cd apps/ad && PYTHONPATH=. pytest tests/test_hr_demo_bootstrap.py -q
```

Expected: PASS.

- [ ] **Step 5: Commit documentation and verifier**

```bash
git add README.md apps/ad/README.md demo/default/README.md scripts/verify-hr-demo.sh apps/ad/tests/test_hr_demo_bootstrap.py
git commit -m "docs(demo): document reproducible HR setup"
```

### Task 5: Build and real-service acceptance

**Files:**
- Modify only if a verified defect is found in the prior tasks.

**Interfaces:**
- The local command `./scripts/verify-hr-demo.sh` succeeds after the database, assets, and services are initialized.

- [ ] **Step 1: Run relevant AD tests**

Run: `cd apps/ad && PYTHONPATH=. pytest tests/test_demo_hr_data.py tests/test_hr_demo_bootstrap.py tests/test_hr_dashboard_drilldown.py -q`

Expected: PASS, with the existing database-dependent drill test permitted to skip only when no local HRRDB is available.

- [ ] **Step 2: Build DA with Java 8**

Run: `cd apps/da && mvn -DskipTests package`

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Perform clean local initialization and service acceptance**

Run:

```bash
./scripts/init-demo-db.sh
./scripts/init-demo-assets.sh
./scripts/insightmind.sh restart
VERIFY_RUNNING_SERVICES=1 ./scripts/verify-hr-demo.sh
```

Expected: both HR graphs parse and load; the dashboard list reports only the two HR IDs; both URLs return HTTP 200.

- [ ] **Step 4: Open both dashboards and exercise drill actions**

Use the local browser to open the two dashboard URLs. Click every indicator card, indicator cell, and chart once; for each available interaction exercise detail drill, business explanation, and dimension drill. Repair any functional error before proceeding.

- [ ] **Step 5: Commit verified repairs and publish branch**

```bash
git status --short
git add <only files repaired during acceptance>
git commit -m "fix(demo): complete HR demo acceptance" # only when repairs exist
git push origin codex/celn-smart-insight-demo-fix
```

## Self-Review

- **Spec coverage:** Task 1 provides synthetic HR data; Task 2 activates HRRDB and removes old database setup; Task 3 activates the checked-in graphs and two dashboards; Task 4 documents and verifies a fresh setup; Task 5 validates build, services, and every drill path. No specified requirement is unassigned.
- **Placeholder scan:** No `TODO`, `TBD`, `implement later`, or unspecified validation steps are present.
- **Type consistency:** `build_demo_rows` and `seed_database` are introduced in Task 1 and used only with the same names in later tasks. `HR_DEMO_DB` is the only HR database variable across tasks.
