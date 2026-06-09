"""
P1: Enhanced Inclusion Dependency (IND) Detection

Extends fk_detector.py with two additional passes:

Pass A — Same-name cross-table columns (no FK suffix required)
  Columns with identical normalized names across tables are checked for
  inclusion.  Direction inferred from cardinality: smaller side → larger side.

Pass B — High-confidence directional inclusion check
  For ALL FK-suffix columns, checks whether src_vals ⊆ pk_vals (complete
  inclusion) rather than just overlap ratio, giving a separate
  "full_inclusion" confidence tier.

Both passes produce DetectedFK objects injected via inject_logical_fks().
"""
from __future__ import annotations

import re
from typing import Any, Dict, List, Set, Tuple

from kg_builder.entities.models import EntityGraph
from kg_builder.parsers.schema_parser import SchemaInfo
from kg_builder.relations.fk_detector import DetectedFK

# FK-suffix pattern (same as fk_detector)
_FK_SUFFIXES = re.compile(
    r'(_id|_ids|_key|_code|_no|_num|_fk|_ref|_uuid|编号|标识|主键)$',
    re.IGNORECASE,
)

_SKIP_NAMES = {
    'id', 'uuid', 'created_at', 'updated_at', 'create_time', 'update_time',
    'created_by', 'updated_by', 'creator', 'updater', 'is_del', 'deleted',
    'version', 'tenant_id', 'sort', 'order_num', 'status', 'type', 'flag',
}

# Minimum values in common to count as inclusion
MIN_COMMON_N = 3


def _value_rows(table_info: Any) -> List[Dict[str, Any]]:
    """Rows used for value matching; falls back for older SchemaInfo objects."""
    return getattr(table_info, "value_sample_rows", None) or table_info.sample_rows


class InclusionDepDetector:
    """
    Two-pass IND detector that complements LogicalFKDetector.

    Pass A: Same-name columns across tables (no FK suffix needed)
    Pass B: Full-inclusion check for FK-suffix columns
    """

    def __init__(
        self,
        min_full_inclusion: float = 0.95,  # A ⊆ B threshold
        min_partial_overlap: float = 0.60,
        min_n: int = MIN_COMMON_N,
    ) -> None:
        self.min_full_inclusion = min_full_inclusion
        self.min_partial_overlap = min_partial_overlap
        self.min_n = min_n

    # ------------------------------------------------------------------ #

    def detect(
        self,
        entity_graph: EntityGraph,
        schema_info: SchemaInfo,
    ) -> List[DetectedFK]:

        # ── Build lookups ─────────────────────────────────────────────── #
        tname_to_pk: Dict[str, str] = {}
        for con in entity_graph.constraints:
            if con.constraint_type == "PRIMARY" and len(con.constrained_columns) == 1:
                tn = con.table_id.split("::")[-1].lower()
                tname_to_pk[tn] = con.constrained_columns[0]

        si_map = {t.name.lower(): t for t in schema_info.tables}

        tname_to_pk_vals: Dict[str, Set[str]] = {}
        for tn, pk_col in tname_to_pk.items():
            ti = si_map.get(tn)
            rows = _value_rows(ti) if ti else []
            if rows:
                vals = {
                    str(r[pk_col]) for r in rows
                    if r.get(pk_col) is not None
                }
                if vals:
                    tname_to_pk_vals[tn] = vals

        declared: Set[Tuple[str, str, str]] = set()
        for con in entity_graph.constraints:
            if con.constraint_type == "FOREIGN" and con.referred_table:
                src = con.table_id.split("::")[-1].lower()
                for col in con.constrained_columns:
                    declared.add((src, col.lower(), con.referred_table.lower()))

        # ── Group columns by normalized name ──────────────────────────── #
        from collections import defaultdict
        name_groups: Dict[str, list] = defaultdict(list)
        for col in entity_graph.columns:
            if col.name.lower() in _SKIP_NAMES:
                continue
            norm = col.normalized_name or col.name.lower()
            name_groups[norm].append(col)

        results: List[DetectedFK] = []
        seen: Set[Tuple[str, str, str]] = set()

        # ── Pass A: Same-name cross-table columns ──────────────────────── #
        for norm_name, group in name_groups.items():
            if len(group) < 2:
                continue
            # Only consider groups where at least one column is a PK
            has_pk = any(c.is_pk for c in group)
            if not has_pk:
                continue
            # Skip if name has FK suffix (already handled by fk_detector)
            if _FK_SUFFIXES.search(norm_name):
                continue

            for col in group:
                if col.is_pk:
                    continue
                if col.name.lower() in _SKIP_NAMES:
                    continue

                src_tn  = col.table_id.split("::")[-1]
                src_tl  = src_tn.lower()

                # Get source column values
                src_vals: Set[str] = set()
                ti_src = si_map.get(src_tl)
                src_rows = _value_rows(ti_src) if ti_src else []
                if src_rows:
                    src_vals = {
                        str(r[col.name]) for r in src_rows
                        if r.get(col.name) is not None
                    }

                for pk_col_entity in group:
                    if not pk_col_entity.is_pk:
                        continue
                    tgt_tn = pk_col_entity.table_id.split("::")[-1]
                    tgt_tl = tgt_tn.lower()
                    if tgt_tl == src_tl:
                        continue

                    key = (src_tl, col.name.lower(), tgt_tl)
                    if key in declared or key in seen:
                        continue

                    # Check value inclusion
                    pk_vals = tname_to_pk_vals.get(tgt_tl, set())
                    if src_vals and pk_vals:
                        common = src_vals & pk_vals
                        ov = len(common) / len(src_vals) if src_vals else 0.0
                        ov_n = len(common)
                    else:
                        ov, ov_n = 0.0, 0

                    if ov >= self.min_full_inclusion and ov_n >= self.min_n:
                        conf   = 0.92
                        method = "same_name+full_inclusion"
                    elif ov >= self.min_partial_overlap and ov_n >= self.min_n:
                        conf   = 0.80
                        method = "same_name+partial_inclusion"
                    elif ov == 0.0 and ov_n == 0:
                        # No sample data — use name match only, low confidence
                        conf   = 0.65
                        method = "same_name_only"
                    else:
                        continue

                    seen.add(key)
                    pk_col_name = tname_to_pk.get(tgt_tl, pk_col_entity.name)
                    results.append(DetectedFK(
                        from_table=src_tn,
                        from_column=col.name,
                        to_table=tgt_tn,
                        to_column=pk_col_name,
                        confidence=conf,
                        method=method,
                        overlap=round(ov, 3),
                    ))

        # ── Pass B: Full-inclusion check for FK-suffix columns ─────────── #
        # (Adds high-confidence "full_inclusion" results on top of fk_detector)
        for col in entity_graph.columns:
            if col.is_pk:
                continue
            cname_lower = col.name.lower()
            if cname_lower in _SKIP_NAMES:
                continue
            if not _FK_SUFFIXES.search(cname_lower):
                continue

            src_tn = col.table_id.split("::")[-1]
            src_tl = src_tn.lower()

            src_vals: Set[str] = set()
            ti_src = si_map.get(src_tl)
            src_rows = _value_rows(ti_src) if ti_src else []
            if src_rows:
                src_vals = {
                    str(r[col.name]) for r in src_rows
                    if r.get(col.name) is not None
                }

            if not src_vals:
                continue

            for tgt_tl, pk_vals in tname_to_pk_vals.items():
                if tgt_tl == src_tl:
                    continue
                key = (src_tl, cname_lower, tgt_tl)
                if key in declared or key in seen:
                    continue

                common = src_vals & pk_vals
                if len(common) < self.min_n:
                    continue

                ov = len(common) / len(src_vals)
                # Only emit if this is a near-complete inclusion (high bar)
                if ov < self.min_full_inclusion:
                    continue

                tgt_tn = next(
                    (t.name for t in entity_graph.tables
                     if t.name.lower() == tgt_tl), tgt_tl
                )
                seen.add(key)
                results.append(DetectedFK(
                    from_table=src_tn,
                    from_column=col.name,
                    to_table=tgt_tn,
                    to_column=tname_to_pk.get(tgt_tl, "id"),
                    confidence=0.88,
                    method="fk_suffix+full_inclusion",
                    overlap=round(ov, 3),
                ))

        results.sort(key=lambda x: -x.confidence)
        return results
