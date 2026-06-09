"""
P2: FK Integrity Checker

Uses sample data already collected during build to detect potential
referential integrity violations.

For each FK relationship (declared + logical), checks:
  - How many distinct values in the FK column do NOT appear in
    the referenced table's PK sample.
  - Reports violation rate and example bad values.

Note: Results are based on sampled data (up to sample_limit rows per table),
so they are indicative, not exhaustive.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Set

from kg_builder.entities.models import EntityGraph
from kg_builder.parsers.schema_parser import SchemaInfo


@dataclass
class FKViolation:
    from_table:    str
    from_column:   str
    to_table:      str
    to_column:     str
    fk_method:     str          # "declared" | "logical" | "comment_ref" | ...
    total_sampled: int          # total non-null values in FK column sample
    missing_count: int          # values not found in referenced PK sample
    violation_rate: float       # missing_count / total_sampled
    example_bad_values: List[Any] = field(default_factory=list)
    note: str = ""


def _value_rows(table_info: Any) -> List[Dict[str, Any]]:
    """Rows used for FK checks; falls back for older SchemaInfo objects."""
    return getattr(table_info, "value_sample_rows", None) or table_info.sample_rows


class FKIntegrityChecker:

    def __init__(self, max_examples: int = 5) -> None:
        self.max_examples = max_examples

    # ------------------------------------------------------------------ #

    def check(
        self,
        entity_graph: EntityGraph,
        schema_info: SchemaInfo,
    ) -> List[FKViolation]:
        si_map = {t.name.lower(): t for t in schema_info.tables}

        # ── Build PK sample-value sets ─────────────────────────────────── #
        pk_vals_map: Dict[str, Set[str]] = {}   # "table_lower::col" → set
        tname_to_pk: Dict[str, str] = {}

        for con in entity_graph.constraints:
            if con.constraint_type == "PRIMARY" and len(con.constrained_columns) == 1:
                tn = con.table_id.split("::")[-1].lower()
                pk_col = con.constrained_columns[0]
                tname_to_pk[tn] = pk_col
                ti = si_map.get(tn)
                rows = _value_rows(ti) if ti else []
                if rows:
                    vals = {
                        str(r[pk_col])
                        for r in rows
                        if r.get(pk_col) is not None
                    }
                    pk_vals_map[f"{tn}::{pk_col.lower()}"] = vals

        # ── Collect all FK relationships ───────────────────────────────── #
        # Each FK: (from_table, from_col, to_table, to_col, method)
        fk_list: List[tuple] = []
        seen_keys: Set[tuple] = set()

        for con in entity_graph.constraints:
            if con.constraint_type != "FOREIGN" or not con.referred_table:
                continue
            from_tn  = con.table_id.split("::")[-1]
            to_tn    = con.referred_table
            method   = "declared" if not con.id.startswith("logicfk::") else (
                con.name.split("_")[2] if len(con.name.split("_")) > 2 else "logical"
            )
            # Determine method from id prefix
            if con.id.startswith("logicfk::"):
                method = "logical"
            elif con.id.startswith("comment_ref::"):
                method = "comment_ref"
            else:
                method = "declared"

            for from_col in con.constrained_columns:
                to_col = (con.referred_columns or [tname_to_pk.get(to_tn.lower(), "id")])[0]
                key = (from_tn.lower(), from_col.lower(), to_tn.lower())
                if key not in seen_keys:
                    seen_keys.add(key)
                    fk_list.append((from_tn, from_col, to_tn, to_col, method))

        # ── Check each FK ──────────────────────────────────────────────── #
        violations: List[FKViolation] = []

        for from_tn, from_col, to_tn, to_col, method in fk_list:
            pk_key = f"{to_tn.lower()}::{to_col.lower()}"
            pk_vals = pk_vals_map.get(pk_key)
            if pk_vals is None:
                # No PK sample available — cannot check
                continue

            from_ti = si_map.get(from_tn.lower())
            from_rows = _value_rows(from_ti) if from_ti else []
            if not from_rows:
                continue

            # Collect FK values (non-null)
            fk_values = [
                r[from_col]
                for r in from_rows
                if r.get(from_col) is not None
            ]
            if not fk_values:
                continue

            total     = len(fk_values)
            bad_vals  = [v for v in fk_values if str(v) not in pk_vals]
            bad_count = len(bad_vals)

            if bad_count == 0:
                continue  # no violation

            vrate = bad_count / total
            # Only report if violation rate is non-trivial (> 2%)
            if vrate < 0.02:
                continue

            # Deduplicate examples
            seen_ex: Set[str] = set()
            examples = []
            for v in bad_vals:
                sv = str(v)
                if sv not in seen_ex:
                    seen_ex.add(sv)
                    examples.append(v)
                if len(examples) >= self.max_examples:
                    break

            note = ""
            if vrate > 0.50:
                note = "⚠ 超过50%的样本值找不到对应主键，可能是逻辑外键误判或数据已损坏"
            elif vrate > 0.20:
                note = "较高违规率，建议检查数据清洗流程"

            violations.append(FKViolation(
                from_table=from_tn,
                from_column=from_col,
                to_table=to_tn,
                to_column=to_col,
                fk_method=method,
                total_sampled=total,
                missing_count=bad_count,
                violation_rate=round(vrate, 4),
                example_bad_values=examples,
                note=note,
            ))

        violations.sort(key=lambda v: -v.violation_rate)
        return violations


def violations_to_dict(violations: List[FKViolation]) -> List[dict]:
    """Serialize violations to JSON-friendly dicts."""
    return [
        {
            "来源表":     v.from_table,
            "外键列":     v.from_column,
            "目标表":     v.to_table,
            "目标主键":   v.to_column,
            "关系类型":   v.fk_method,
            "采样总数":   v.total_sampled,
            "缺失数量":   v.missing_count,
            "违规率":     f"{v.violation_rate:.1%}",
            "示例脏值":   [str(x) for x in v.example_bad_values],
            "备注":       v.note,
        }
        for v in violations
    ]
