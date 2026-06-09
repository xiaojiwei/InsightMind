"""
main.py — CLI entry point for the Watson Discovery-style KG builder.

Usage:
  python main.py build  --config config.yaml --output output/kg.ttl
  python main.py query  --graph output/kg.ttl --find-related orders
  python main.py query  --graph output/kg.ttl --similar-columns user_id
  python main.py query  --graph output/kg.ttl --fk-graph
  python main.py query  --graph output/kg.ttl --sparql "SELECT ?t WHERE {?t a db:Table}"
  python main.py export --graph output/kg.ttl --format jsonld --output output/kg.jsonld
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import yaml
from rich.console import Console
from rich.table import Table as RichTable

from kg_builder.connectors.base import DataSourceConfig
from kg_builder.parsers.schema_parser import SchemaParser
from kg_builder.parsers.data_sampler import DataSampler
from kg_builder.entities.extractor import EntityExtractor
from kg_builder.relations.explicit import ExplicitRelationExtractor
from kg_builder.relations.implicit import ImplicitRelationExtractor
from kg_builder.ontology.rdf_builder import RDFBuilder
from kg_builder.query.sparql_api import SPARQLApi


console = Console()


# ── Connector factory ────────────────────────────────────────────────── #

def _make_connector(cfg: DataSourceConfig):
    db_type = cfg.db_type.lower()
    if db_type == "mysql":
        from kg_builder.connectors.mysql import MySQLConnector
        return MySQLConnector(cfg)
    elif db_type in ("mssql", "sqlserver", "mssqlserver"):
        from kg_builder.connectors.mssql import MSSQLConnector
        return MSSQLConnector(cfg)
    elif db_type == "oracle":
        from kg_builder.connectors.oracle import OracleConnector
        return OracleConnector(cfg)
    elif db_type == "sqlite":
        # Convenience: SQLite via SQLAlchemy for testing
        from sqlalchemy import create_engine
        from kg_builder.connectors.base import BaseConnector

        class _SQLiteConnector(BaseConnector):
            def _build_url(self):
                return f"sqlite:///{self.config.database}"

        return _SQLiteConnector(cfg)
    else:
        console.print(f"[red]Unknown db_type '{db_type}'[/red]")
        sys.exit(1)


# ── Build command ────────────────────────────────────────────────────── #

def cmd_build(args) -> None:
    config_path = args.config
    if not Path(config_path).exists():
        console.print(f"[red]Config file not found: {config_path}[/red]")
        sys.exit(1)

    with open(config_path, encoding="utf-8") as f:
        config = yaml.safe_load(f)

    datasources = config.get("datasources", [])
    if not datasources:
        console.print("[red]No datasources defined in config.[/red]")
        sys.exit(1)

    settings = config.get("settings", {})
    enable_sampling    = settings.get("enable_sampling", True)
    enable_implicit    = settings.get("enable_implicit_relations", True)
    enable_reasoning   = settings.get("enable_owl_reasoning", False)
    similarity_thresh  = settings.get("similarity_threshold", 0.85)
    synonyms_path      = settings.get("synonyms_path", "synonyms.yaml")
    st_model           = settings.get("sentence_transformer_model",
                                      "paraphrase-multilingual-MiniLM-L12-v2")

    builder = RDFBuilder(include_owl_schema=True)
    explicit_extractor = ExplicitRelationExtractor()
    entity_extractor   = EntityExtractor(synonyms_path=synonyms_path)

    for ds_cfg in datasources:
        ds = DataSourceConfig(
            name=ds_cfg["name"],
            db_type=ds_cfg["type"],
            host=ds_cfg.get("host", "localhost"),
            port=ds_cfg.get("port", 3306),
            database=ds_cfg.get("database", ""),
            username=ds_cfg.get("username", ""),
            password=ds_cfg.get("password", ""),
            service_name=ds_cfg.get("service_name", ""),
            sid=ds_cfg.get("sid", ""),
            windows_auth=ds_cfg.get("windows_auth", False),
            driver=ds_cfg.get("driver", ""),
            sample_limit=ds_cfg.get("sample_limit", 1000),
            exclude_tables=ds_cfg.get("exclude_tables", []),
        )

        connector = _make_connector(ds)

        console.print(f"[cyan]Connecting to [{ds.db_type}] {ds.name}…[/cyan]")
        if not connector.test_connection():
            console.print(f"[red]  Connection failed — skipping.[/red]")
            continue

        schema_name = ds_cfg.get("schema", None)
        console.print(f"  Parsing schema '{schema_name or ds.database}'…")
        parser = SchemaParser(connector)
        schema_info = parser.parse(schema_name=schema_name)
        console.print(f"  → {len(schema_info.tables)} tables found.")

        if enable_sampling:
            console.print("  Sampling data…")
            sampler = DataSampler(connector, limit=ds.sample_limit)
            schema_info = sampler.sample_schema(schema_info)

        console.print("  Extracting entities…")
        entity_graph = entity_extractor.extract(schema_info)
        console.print(
            f"  → {len(entity_graph.tables)} tables, "
            f"{len(entity_graph.columns)} columns, "
            f"{len(entity_graph.constraints)} constraints."
        )

        console.print("  Extracting explicit relations…")
        relations = explicit_extractor.extract(entity_graph)
        console.print(f"  → {len(relations)} explicit relations.")

        if enable_implicit:
            console.print("  Discovering implicit relations (Sentence-Transformers)…")
            implicit_extractor = ImplicitRelationExtractor(
                model_name=st_model,
                similarity_threshold=similarity_thresh,
            )
            implicit_rels = implicit_extractor.extract(entity_graph)
            console.print(f"  → {len(implicit_rels)} implicit relations.")
            relations.extend(implicit_rels)

        console.print("  Building RDF graph…")
        builder.build(entity_graph, relations)

        connector.close()

    if enable_reasoning:
        console.print("[cyan]Applying OWL-RL reasoning…[/cyan]")
        builder.apply_reasoning()

    output_path = args.output or "output/kg.ttl"
    console.print(f"[cyan]Saving graph → {output_path}[/cyan]")
    fmt = "json-ld" if output_path.endswith((".jsonld", ".json")) else "turtle"
    builder.save(output_path, fmt=fmt)

    triple_count = len(builder.graph)
    console.print(f"[green]Done. Graph contains {triple_count} triples.[/green]")


# ── Query command ────────────────────────────────────────────────────── #

def cmd_query(args) -> None:
    graph_path = args.graph
    if not Path(graph_path).exists():
        console.print(f"[red]Graph file not found: {graph_path}[/red]")
        sys.exit(1)

    api = SPARQLApi.from_file(graph_path)

    if args.find_related:
        rows = api.find_related_tables(args.find_related)
        _print_table("Related Tables", rows, ["relName"])

    elif args.similar_columns:
        rows = api.find_similar_columns(args.similar_columns)
        _print_table("Similar Columns", rows, ["srcName", "simName", "simTableName"])

    elif args.table_schema:
        rows = api.get_table_schema(args.table_schema)
        _print_table(f"Schema: {args.table_schema}", rows,
                     ["colName", "colType", "isNullable", "isPK", "comment"])

    elif args.fk_graph:
        rows = api.get_fk_graph()
        _print_table("FK Dependency Graph", rows,
                     ["srcTableName", "srcColName", "tgtTableName"])

    elif args.potential_joins:
        t1, t2 = args.potential_joins.split(",", 1)
        rows = api.find_potential_joins(t1.strip(), t2.strip())
        _print_table(f"Potential JOINs: {t1} ↔ {t2}", rows,
                     ["col1Name", "rel", "col2Name"])

    elif args.search_comment:
        rows = api.search_by_comment(args.search_comment)
        _print_table(f"Comment search: '{args.search_comment}'", rows,
                     ["entityType", "entityName", "comment"])

    elif args.sparql:
        rows = api.run_raw(args.sparql)
        if rows:
            _print_table("SPARQL Results", rows, list(rows[0].keys()))
        else:
            console.print("(no results)")

    else:
        console.print("[yellow]No query option specified. Use --help.[/yellow]")


# ── Export command ───────────────────────────────────────────────────── #

def cmd_export(args) -> None:
    from rdflib import Graph
    g = Graph()
    g.parse(args.graph,
            format="json-ld" if args.graph.endswith(".json") else "turtle")
    fmt = args.format.lower()
    fmt_map = {"jsonld": "json-ld", "turtle": "turtle", "nt": "nt", "n3": "n3"}
    out_fmt = fmt_map.get(fmt, "turtle")
    out_path = args.output or f"output/kg.{fmt}"
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    g.serialize(destination=out_path, format=out_fmt)
    console.print(f"[green]Exported to {out_path} ({out_fmt})[/green]")


# ── Display helper ───────────────────────────────────────────────────── #

def _print_table(title: str, rows: list, cols: list) -> None:
    if not rows:
        console.print(f"[yellow]{title}: (no results)[/yellow]")
        return
    t = RichTable(title=title, show_lines=False)
    for c in cols:
        t.add_column(c, style="cyan")
    for row in rows:
        t.add_row(*[row.get(c, "") for c in cols])
    console.print(t)


# ── Argument parser ──────────────────────────────────────────────────── #

def main():
    parser = argparse.ArgumentParser(
        prog="kg_builder",
        description="Watson Discovery-style Knowledge Graph Builder",
    )
    sub = parser.add_subparsers(dest="command")

    # build
    p_build = sub.add_parser("build", help="Build knowledge graph from DB")
    p_build.add_argument("--config",  default="config.yaml", help="Config YAML file")
    p_build.add_argument("--output",  default="output/kg.ttl", help="Output Turtle file")

    # query
    p_query = sub.add_parser("query", help="Query an existing knowledge graph")
    p_query.add_argument("--graph", required=True, help="Path to .ttl or .jsonld file")
    p_query.add_argument("--find-related",    metavar="TABLE",   help="Find tables related via FK")
    p_query.add_argument("--similar-columns", metavar="COLUMN",  help="Find semantically similar columns")
    p_query.add_argument("--table-schema",    metavar="TABLE",   help="Show all columns of a table")
    p_query.add_argument("--fk-graph",        action="store_true", help="Show full FK dependency graph")
    p_query.add_argument("--potential-joins",  metavar="T1,T2",  help="Find potential JOINs between two tables")
    p_query.add_argument("--search-comment",  metavar="KW",      help="Search comments by keyword")
    p_query.add_argument("--sparql",          metavar="QUERY",   help="Run raw SPARQL SELECT")

    # export
    p_exp = sub.add_parser("export", help="Re-export graph in another format")
    p_exp.add_argument("--graph",   required=True)
    p_exp.add_argument("--format",  default="jsonld",
                       choices=["turtle", "jsonld", "nt", "n3"])
    p_exp.add_argument("--output",  default=None)

    args = parser.parse_args()

    if args.command == "build":
        cmd_build(args)
    elif args.command == "query":
        cmd_query(args)
    elif args.command == "export":
        cmd_export(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
