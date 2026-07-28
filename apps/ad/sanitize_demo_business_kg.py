#!/usr/bin/env python3
"""Keep a demo business KG aligned with the tables installed by its demo DB.

The call-quality demo intentionally installs only ``im_call_quality_fact``.
Older exported indicator files can still contain measures for retired CRM and
funnel tables, which makes semantic validation (and NLQ suggestions) expose
queries that can never run.  This utility removes those unavailable measures
and their applications before the file is loaded by the data-agent.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from rdflib import Graph, Namespace, RDF


IND = Namespace("http://indicator.insightmind.com/ontology#")


def _table_name(graph: Graph, table) -> str:
    return str(graph.value(table, IND.tableName) or "")


def sanitize(path: Path, allowed_tables: set[str]) -> list[str]:
    graph = Graph()
    graph.parse(path, format="turtle")

    removed_codes: list[str] = []
    for measure in list(graph.subjects(RDF.type, IND.Measure)):
        apps = list(graph.objects(measure, IND.hasMeasureApp))
        usable_apps = [
            app
            for app in apps
            if _table_name(graph, graph.value(app, IND.appliesToTable)) in allowed_tables
        ]
        if apps and usable_apps:
            continue

        code = str(graph.value(measure, IND.code) or measure)
        removed_codes.append(code)
        for app in apps:
            # Applications are exclusively owned by one measure in this KG.
            graph.remove((app, None, None))
            graph.remove((None, None, app))
        graph.remove((measure, None, None))
        graph.remove((None, None, measure))

    graph.serialize(path, format="turtle")
    return sorted(removed_codes)


def main() -> None:
    parser = argparse.ArgumentParser(description="Remove demo KG measures backed by unavailable tables")
    parser.add_argument("path", type=Path)
    parser.add_argument("--allow-table", action="append", required=True, dest="tables")
    args = parser.parse_args()

    removed = sanitize(args.path, set(args.tables))
    print(f"Sanitized {args.path}: removed {len(removed)} unavailable measures")
    for code in removed:
        print(f"  - {code}")


if __name__ == "__main__":
    main()
