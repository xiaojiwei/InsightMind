"""
Run the AD Semantic SQL API over the MySQL wire protocol.

Example:
  venv/bin/python ad_sql_server.py --host 127.0.0.1 --port 13306
  mysql -h 127.0.0.1 -P 13306 -u ad -D ad
"""
from __future__ import annotations

import argparse
import asyncio


def _service_factory():
    from kg_builder.semantic import AdSemanticService
    from web_app import _pivot_catalog, _pivot_da_filters, _pivot_da_query

    return AdSemanticService(
        catalog=_pivot_catalog(),
        da_query=_pivot_da_query,
        da_filter_builder=_pivot_da_filters,
    )


async def _main() -> None:
    from kg_builder.semantic.sql_api import AdSqlEngine, serve_mysql

    parser = argparse.ArgumentParser(description="AD Semantic SQL API server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=13306)
    args = parser.parse_args()

    engine = AdSqlEngine(service_factory=_service_factory)
    print(f"AD SQL API listening on mysql://{args.host}:{args.port}/ad")
    await serve_mysql(engine, host=args.host, port=args.port)


if __name__ == "__main__":
    asyncio.run(_main())
