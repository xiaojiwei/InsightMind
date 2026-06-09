"""Data sampler — collects value statistics per column via SQL sampling."""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

from sqlalchemy import text

from kg_builder.connectors.base import BaseConnector
from kg_builder.parsers.schema_parser import SchemaInfo, TableInfo

logger = logging.getLogger(__name__)


# ── Regex patterns for common data formats ──────────────────────────── #
_PATTERNS: Dict[str, re.Pattern] = {
    "date_iso":   re.compile(r"^\d{4}-\d{2}-\d{2}"),
    "datetime":   re.compile(r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}"),
    "phone_cn":   re.compile(r"^1[3-9]\d{9}$"),
    "email":      re.compile(r"^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$"),
    "id_card_cn": re.compile(r"^\d{17}[\dXx]$"),
    "uuid":       re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"),
    "url":        re.compile(r"^https?://"),
}


@dataclass
class ColumnStats:
    column_name: str
    null_count: int = 0
    total_count: int = 0
    null_rate: float = 0.0
    cardinality: int = 0          # distinct value count (from sample)
    # Numeric
    min_val: Optional[float] = None
    max_val: Optional[float] = None
    avg_val: Optional[float] = None
    # String
    avg_length: Optional[float] = None
    max_length: Optional[int] = None
    # Enum values (when cardinality <= 20)
    top_values: List[Any] = field(default_factory=list)
    # Detected format patterns
    detected_patterns: List[str] = field(default_factory=list)


class DataSampler:
    """Sample up to *limit* rows per table and compute column statistics.

    For MySQL, uses randomised sampling (WHERE RAND() < rate) when the
    estimated row count exceeds 2× the sample limit, to avoid systematic
    bias from front-of-table sampling.
    """

    def __init__(self, connector: BaseConnector, limit: int = 1000) -> None:
        self.connector = connector
        self.limit = limit

    def sample_schema(self, schema_info: SchemaInfo) -> SchemaInfo:
        """Enrich schema_info.tables[*].columns[*].stats in-place and return."""
        total = len(schema_info.tables)
        for i, table in enumerate(schema_info.tables, 1):
            # Use the table's own schema (database name) rather than the
            # top-level schema_info.schema_name which may be "*" in
            # all-databases mode.
            actual_schema = table.schema or schema_info.schema_name or None
            if actual_schema == "*":
                actual_schema = None
            logger.info("[%d/%d] Sampling %s.%s …", i, total, actual_schema, table.name)
            self._sample_table(table, actual_schema)
        return schema_info

    # ------------------------------------------------------------------ #

    def _sample_table(self, table: TableInfo, schema: str) -> None:
        db_type = self.connector.config.db_type
        fqn = self._fqn(table.name, schema, db_type)

        col_names = [c.name for c in table.columns]
        if not col_names:
            return

        rows = self._fetch_rows(fqn, schema, table.name, db_type)
        if rows is None:
            logger.warning(
                "Sampling failed for table %s.%s — column stats will be empty.",
                schema, table.name,
            )
            return
        if not rows:
            return

        sampled_rows = [
            {cname: row[i] for i, cname in enumerate(col_names) if i < len(row)}
            for row in rows
        ]
        table.value_sample_rows = sampled_rows
        # Save up to 20 rows as raw dicts for ABox individual creation/UI preview.
        table.sample_rows = sampled_rows[:20]

        # Build per-column value lists
        col_values: Dict[str, List] = {c: [] for c in col_names}
        for row in rows:
            for i, cname in enumerate(col_names):
                try:
                    col_values[cname].append(row[i])
                except IndexError:
                    pass

        for col in table.columns:
            vals = col_values.get(col.name, [])
            col.stats = self._compute_stats(col.name, vals)

    def _fqn(self, table_name: str, schema: str, db_type: str) -> str:
        if db_type == "mssql":
            return f"[{schema}].[{table_name}]" if schema else f"[{table_name}]"
        elif db_type == "oracle":
            return f'"{schema}"."{table_name}"' if schema else f'"{table_name}"'
        else:
            # MySQL / SQLite / generic — use backtick quoting
            return f"`{schema}`.`{table_name}`" if schema else f"`{table_name}`"

    def _fetch_rows(
        self, fqn: str, schema: str, table_name: str, db_type: str
    ) -> Optional[List]:
        """Return sampled rows, or None on unrecoverable error."""
        engine = self.connector.connect()
        try:
            with engine.connect() as conn:
                if db_type in ("mysql", "doris", "starrocks"):
                    return self._mysql_random_sample(conn, fqn, schema, table_name)
                elif db_type == "mssql":
                    rows = conn.execute(
                        text(f"SELECT TOP {self.limit} * FROM {fqn}")
                    ).fetchall()
                    return rows
                elif db_type == "oracle":
                    rows = conn.execute(
                        text(f"SELECT * FROM {fqn} WHERE ROWNUM <= {self.limit}")
                    ).fetchall()
                    return rows
                else:
                    rows = conn.execute(
                        text(f"SELECT * FROM {fqn} LIMIT {self.limit}")
                    ).fetchall()
                    return rows
        except Exception as exc:
            logger.warning(
                "Sampling error for %s.%s (%s): %s",
                schema, table_name, db_type, exc,
            )
            return None

    def _mysql_random_sample(self, conn, fqn: str, schema: str, table_name: str) -> List:
        """
        For MySQL: estimate row count and use probabilistic sampling
        (WHERE RAND() < rate) when the table is large enough to introduce
        front-of-table bias.  Falls back to plain LIMIT for small tables.
        """
        # Approximate row count from information_schema (fast, no full scan)
        try:
            schema_filter = f"AND TABLE_SCHEMA = '{schema}'" if schema else ""
            result = conn.execute(text(
                f"SELECT TABLE_ROWS FROM information_schema.TABLES "
                f"WHERE TABLE_NAME = '{table_name}' {schema_filter} LIMIT 1"
            )).fetchone()
            approx_count = int(result[0]) if result and result[0] else 0
        except Exception:
            approx_count = 0

        if approx_count > self.limit * 2:
            # Probabilistic sample: slightly over-sample to ensure ~limit rows
            rate = min(1.0, (self.limit * 1.5) / approx_count)
            rows = conn.execute(
                text(f"SELECT * FROM {fqn} WHERE RAND() < {rate:.6f} LIMIT {self.limit}")
            ).fetchall()
        else:
            rows = conn.execute(
                text(f"SELECT * FROM {fqn} LIMIT {self.limit}")
            ).fetchall()
        return rows

    def _compute_stats(self, col_name: str, values: List) -> ColumnStats:
        total = len(values)
        nulls = sum(1 for v in values if v is None)
        non_null = [v for v in values if v is not None]
        distinct = len(set(str(v) for v in non_null))

        stats = ColumnStats(
            column_name=col_name,
            null_count=nulls,
            total_count=total,
            null_rate=round(nulls / total, 4) if total else 0.0,
            cardinality=distinct,
        )

        if not non_null:
            return stats

        # Numeric stats
        numeric_vals = []
        for v in non_null:
            try:
                numeric_vals.append(float(v))
            except (TypeError, ValueError):
                pass

        if numeric_vals:
            stats.min_val = round(min(numeric_vals), 6)
            stats.max_val = round(max(numeric_vals), 6)
            stats.avg_val = round(sum(numeric_vals) / len(numeric_vals), 6)

        # String stats
        str_vals = [str(v) for v in non_null]
        lengths = [len(s) for s in str_vals]
        if lengths:
            stats.avg_length = round(sum(lengths) / len(lengths), 2)
            stats.max_length = max(lengths)

        # Top-N enum values (cardinality <= 20)
        if distinct <= 20:
            from collections import Counter
            counter = Counter(str(v) for v in non_null)
            stats.top_values = [v for v, _ in counter.most_common(10)]

        # Format pattern detection (sample up to 200 non-null string values)
        sample = str_vals[:200]
        for pattern_name, pattern_re in _PATTERNS.items():
            hits = sum(1 for s in sample if pattern_re.match(s))
            if hits / len(sample) >= 0.8:          # 80 %+ match → detected
                stats.detected_patterns.append(pattern_name)

        return stats
