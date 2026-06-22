# Repository Guidelines

## Project Structure & Module Organization

This is a Python knowledge-graph builder with both CLI and FastAPI entry points. Core package code lives in `kg_builder/`, organized by responsibility: `connectors/` for database access, `parsers/` for schema and sample extraction, `entities/` and `relations/` for KG discovery, `ontology/` for RDF/OWL generation, `query/` for SPARQL/path lookup, and `business_kg/` for indicator-oriented graph logic. The web UI template is `kg_builder/web/templates/index.html`. Top-level scripts such as `main.py`, `web_app.py`, `generate_tpcds_bkg.py`, and `validate_indicators.py` are runnable workflows. Configuration and reference data are stored in `config.yaml`, `fk_hints.yaml`, `synonyms.yaml`, and `tpcds_schema.sql`. Generated graph artifacts and exports belong in `output/`.

## Build, Test, and Development Commands

- `python -m venv venv && source venv/bin/activate`: create and activate a local environment.
- `pip install -r requirements.txt`: install fast core runtime dependencies.
- `pip install -r requirements-full.txt`: install all optional AD dependencies for full analysis, extra DB drivers, and tests.
- `python main.py build --config config.yaml --output output/kg.ttl`: build a KG from configured datasources.
- `python main.py query --graph output/kg.ttl --fk-graph`: inspect inferred foreign-key relationships.
- `python web_app.py`: start the FastAPI UI, normally at `http://localhost:8000`.
- `python validate_indicators.py`: run the current indicator validation workflow.

## Coding Style & Naming Conventions

Use Python 3 with 4-space indentation, type hints where they clarify data shape, and `pathlib.Path` for filesystem paths. Keep modules focused on one domain area and follow existing naming patterns: `snake_case.py` files, `PascalCase` classes, `snake_case` functions, and explicit command helpers such as `cmd_build`. Prefer structured parsing and existing helpers over ad hoc string handling.

## Testing Guidelines

There is no formal test suite checked in yet. For new behavior, add focused `pytest` tests under `tests/` using names like `test_schema_parser.py` and `test_detects_explicit_fk`. Until a suite exists, verify changes with the relevant script: build a small KG, query it, and run `validate_indicators.py` for business KG changes.

## Commit & Pull Request Guidelines

Git history is minimal and uses short imperative messages such as `Update config.yaml`. Keep commits concise and scoped, for example `Add SQLite relation parser test`. Pull requests should describe the workflow changed, list manual verification commands, mention config or schema changes, and include screenshots for web UI changes.

## Security & Configuration Tips

Do not commit credentials from `.env` or datasource passwords in `config.yaml`. Keep generated logs and large outputs out of review unless they are intentional fixtures.
