"""Whitelist database structure snapshots and emit structural change events."""

from __future__ import annotations

import hashlib
import json
from typing import Any

from .sanitizer import sanitize_text, sha256_text


def build_snapshot(schema_info: Any) -> tuple[str, dict[str, Any]]:
    source_raw = "|".join([
        str(getattr(schema_info, "db_type", "")), str(getattr(schema_info, "host", "")),
        str(getattr(schema_info, "port", "")), str(getattr(schema_info, "database", "")),
        str(getattr(schema_info, "schema_name", "")),
    ])
    datasource_key = sha256_text(source_raw)
    tables: dict[str, Any] = {}
    for table in sorted(getattr(schema_info, "tables", []) or [], key=lambda item: item.name):
        columns = {}
        primary_keys = set(getattr(table, "primary_keys", []) or [])
        for column in sorted(getattr(table, "columns", []) or [], key=lambda item: item.name):
            columns[str(column.name)] = {
                "dataType": str(getattr(column, "data_type", "")),
                "nullable": bool(getattr(column, "is_nullable", False)),
                "primaryKey": bool(getattr(column, "is_pk", False) or column.name in primary_keys),
                "comment": sanitize_text(getattr(column, "comment", ""), max_chars=500),
            }
        tables[str(table.name)] = {
            "schema": str(getattr(table, "schema", "")),
            "view": bool(getattr(table, "is_view", False)),
            "comment": sanitize_text(getattr(table, "comment", ""), max_chars=500),
            "columns": columns,
        }
    snapshot = {"schemaName": str(getattr(schema_info, "schema_name", "")), "tables": tables}
    canonical = json.dumps(snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    snapshot["schemaHash"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return datasource_key, snapshot


def diff_snapshots(previous: dict[str, Any], current: dict[str, Any]) -> list[dict[str, Any]]:
    before_tables = previous.get("tables") or {}
    after_tables = current.get("tables") or {}
    changes: list[dict[str, Any]] = []
    for table_name in sorted(set(after_tables) - set(before_tables)):
        changes.append({"eventName": "TABLE_ADDED", "table": table_name, "after": after_tables[table_name]})
    for table_name in sorted(set(before_tables) - set(after_tables)):
        changes.append({"eventName": "TABLE_REMOVED", "table": table_name, "before": before_tables[table_name]})
    for table_name in sorted(set(before_tables) & set(after_tables)):
        before = before_tables[table_name]
        after = after_tables[table_name]
        if before.get("comment") != after.get("comment"):
            changes.append({
                "eventName": "TABLE_COMMENT_CHANGED", "table": table_name,
                "before": before.get("comment"), "after": after.get("comment"),
            })
        before_cols = before.get("columns") or {}
        after_cols = after.get("columns") or {}
        for column in sorted(set(after_cols) - set(before_cols)):
            changes.append({"eventName": "COLUMN_ADDED", "table": table_name, "column": column, "after": after_cols[column]})
        for column in sorted(set(before_cols) - set(after_cols)):
            changes.append({"eventName": "COLUMN_REMOVED", "table": table_name, "column": column, "before": before_cols[column]})
        for column in sorted(set(before_cols) & set(after_cols)):
            old = before_cols[column]
            new = after_cols[column]
            for field, event_name in (
                ("dataType", "COLUMN_TYPE_CHANGED"),
                ("nullable", "COLUMN_NULLABILITY_CHANGED"),
                ("comment", "COLUMN_COMMENT_CHANGED"),
                ("primaryKey", "PRIMARY_KEY_CHANGED"),
            ):
                if old.get(field) != new.get(field):
                    changes.append({
                        "eventName": event_name, "table": table_name, "column": column,
                        "field": field, "before": old.get(field), "after": new.get(field),
                    })
    return changes
