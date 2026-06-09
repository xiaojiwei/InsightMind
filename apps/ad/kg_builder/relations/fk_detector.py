"""Logical FK detector — infers undeclared foreign keys via name heuristics
and value-overlap sampling.

Algorithm (per source column)
------------------------------
1. Strip FK suffix (_id/_key/_code/…) to get a stem.
2. Score every *other* table by name similarity to the stem.
3. For each name-candidate, compute value-overlap ratio against its PK.
4. **Dedup / pick-best logic** (reduces false positives):
   - When ≥1 candidate has value overlap ≥ min_overlap  →  keep only the
     candidate(s) with the highest overlap (ties allowed within 5 pp).
   - When no value data is available (empty tables)      →  keep only the
     single highest name-score candidate.
   - Always skip self-references (to_table == from_table).
5. Assign confidence:
     name_exact  + overlap ≥ 0.50  → 0.95
     name_fuzzy  + overlap ≥ 0.50  → 0.85
     name_exact  only               → 0.70  (potentialFK in graph)
     name_fuzzy  only               → 0.60  (potentialFK)
     value_only  ≥ 0.70             → 0.75  (potentialFK)
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from difflib import SequenceMatcher
from typing import Any, Dict, List, Optional, Set, Tuple

from kg_builder.entities.models import EntityGraph, ConstraintEntity
from kg_builder.parsers.schema_parser import SchemaInfo

# Promote to actual FK (db:references) when confidence ≥ this
FK_THRESHOLD = 0.80

_FK_SUFFIXES = re.compile(
    r'(_id|_ids|_key|_code|_no|_num|_fk|_ref|_uuid|编号|标识|主键)$',
    re.IGNORECASE,
)

_SKIP_NAMES = {
    'id', 'uuid', 'created_at', 'updated_at', 'create_time', 'update_time',
    'created_by', 'updated_by', 'creator', 'updater', 'is_del', 'deleted',
    'version', 'tenant_id', 'sort', 'order_num', 'status', 'type', 'flag',
}


@dataclass
class DetectedFK:
    from_table:  str
    from_column: str
    to_table:    str
    to_column:   str
    confidence:  float
    method:      str
    overlap:     float = 0.0   # value-overlap ratio for transparency
    from_schema: Optional[str] = None
    to_schema:   Optional[str] = None


def _table_schema(table_id: str) -> str:
    parts = table_id.split("::")
    return parts[-2] if len(parts) >= 3 else ""


def _table_name(table_id: str) -> str:
    parts = table_id.split("::")
    return parts[-1] if parts else table_id


def _value_rows(table_info: Any) -> List[Dict[str, Any]]:
    """Rows used for value matching; falls back for older SchemaInfo objects."""
    return getattr(table_info, "value_sample_rows", None) or table_info.sample_rows


class LogicalFKDetector:

    def __init__(
        self,
        min_name_score: float = 0.75,
        min_overlap:    float = 0.40,
        min_overlap_n:  int   = 3,
    ) -> None:
        self.min_name_score = min_name_score
        self.min_overlap    = min_overlap
        self.min_overlap_n  = min_overlap_n

    # ------------------------------------------------------------------ #

    def detect(self, entity_graph: EntityGraph, schema_info: SchemaInfo) -> List[DetectedFK]:

        # ── Lookups ──────────────────────────────────────────────────── #
        table_entries = [
            (_table_schema(t.id), t.name, t.id)
            for t in entity_graph.tables
        ]
        all_tkeys = [(schema.lower(), name.lower()) for schema, name, _ in table_entries]

        # (schema_lower, table_lower) → single PK column name (skip composite PKs)
        table_key_to_pk: Dict[Tuple[str, str], str] = {}
        for con in entity_graph.constraints:
            if con.constraint_type == "PRIMARY" and len(con.constrained_columns) == 1:
                key = (_table_schema(con.table_id).lower(), _table_name(con.table_id).lower())
                table_key_to_pk[key] = con.constrained_columns[0]

        # (schema_lower, table_lower) → set of PK sample values
        si_map = {
            ((t.schema or schema_info.schema_name or "").lower(), t.name.lower()): t
            for t in schema_info.tables
        }
        table_key_to_pk_vals: Dict[Tuple[str, str], Set[str]] = {}
        for tkey, pk_col in table_key_to_pk.items():
            ti = si_map.get(tkey)
            rows = _value_rows(ti) if ti else []
            if rows:
                vals = {str(r[pk_col]) for r in rows if r.get(pk_col) is not None}
                if vals:
                    table_key_to_pk_vals[tkey] = vals

        # Already-declared FKs to skip
        declared: Set[Tuple[str, str, str, str, str]] = set()
        for con in entity_graph.constraints:
            if con.constraint_type == "FOREIGN" and con.referred_table:
                src_schema = _table_schema(con.table_id).lower()
                src = _table_name(con.table_id).lower()
                ref_schema = (con.referred_schema or src_schema).lower()
                for col in con.constrained_columns:
                    declared.add((src_schema, src, col.lower(), ref_schema, con.referred_table.lower()))

        results: List[DetectedFK] = []
        seen:    Set[Tuple[str, str, str, str, str]] = set()

        # ── Per-column ───────────────────────────────────────────────── #
        for col in entity_graph.columns:
            if col.is_pk:
                continue
            cname = col.name
            cname_lower = cname.lower()
            if cname_lower in _SKIP_NAMES:
                continue

            src_schema = _table_schema(col.table_id)
            src_tn = _table_name(col.table_id)
            src_key = (src_schema.lower(), src_tn.lower())
            src_tl = src_tn.lower()

            stem, has_suffix = self._stem(cname_lower)
            if not stem or len(stem) < 2:
                continue

            # ── Step 1: collect name-score candidates ─────────────── #
            candidates: List[Tuple[Tuple[str, str], float]] = []   # ((schema, tname), score)
            for tkey in all_tkeys:
                _, tname = tkey
                if tkey == src_key:            # skip self
                    continue
                if tkey not in table_key_to_pk:   # must have a known PK
                    continue
                score = self._name_score(stem, tname)
                if score >= self.min_name_score:
                    candidates.append((tkey, score))

            if not candidates:
                continue

            # Sort by name_score desc
            candidates.sort(key=lambda x: -x[1])

            # ── Step 2: value overlap for each candidate ───────────── #
            src_vals: Set[str] = set()
            ti_src = si_map.get(src_key)
            src_rows = _value_rows(ti_src) if ti_src else []
            if src_rows:
                src_vals = {
                    str(r[cname]) for r in src_rows
                    if r.get(cname) is not None
                }

            scored: List[Tuple[Tuple[str, str], float, float, str, int]] = []  # (table_key, ns, ov, pk, overlap_n)
            for tkey, nscore in candidates:
                pk_col = table_key_to_pk.get(tkey, "id")
                pk_vals = table_key_to_pk_vals.get(tkey, set())

                if src_vals and pk_vals:
                    common = src_vals & pk_vals
                    ov = len(common) / len(src_vals) if src_vals else 0.0
                    ov_n = len(common)
                else:
                    ov, ov_n = 0.0, 0

                scored.append((tkey, nscore, ov, pk_col, ov_n))

            # ── Step 3: pick-best dedup ────────────────────────────── #
            # Candidates with validated overlap
            overlapped = [(t, ns, ov, pk, ovn) for t, ns, ov, pk, ovn in scored
                          if ov >= self.min_overlap and ovn >= self.min_overlap_n]

            if overlapped:
                # Keep only those within 5pp of the max overlap
                max_ov = max(ov for _, _, ov, _, _ in overlapped)
                best = [(t, ns, ov, pk, ovn) for t, ns, ov, pk, ovn in overlapped
                        if ov >= max_ov - 0.05]
            else:
                # No overlap data — keep only the single top name-score candidate
                best = [scored[0]]

            # ── Step 4: emit results ───────────────────────────────── #
            for tkey, nscore, ov, pk_col, ov_n in best:
                tgt_schema, tname = tkey
                key = (src_schema.lower(), src_tl, cname_lower, tgt_schema, tname)
                if key in declared or key in seen:
                    continue
                seen.add(key)

                is_exact = nscore >= 0.99
                has_ov   = ov >= self.min_overlap and ov_n >= self.min_overlap_n

                if is_exact and has_ov:
                    conf, method = 0.95, "name_exact+value"
                elif is_exact:
                    conf, method = 0.70, "name_exact"
                elif has_ov:
                    conf, method = 0.85, "name_fuzzy+value"
                else:
                    conf, method = 0.60, "name_fuzzy"

                results.append(DetectedFK(
                    from_table=src_tn,
                    from_column=cname,
                    to_table=tname,
                    to_column=pk_col,
                    confidence=conf,
                    method=method,
                    overlap=round(ov, 3),
                    from_schema=src_schema,
                    to_schema=tgt_schema,
                ))

            # ── Step 5: value-only pass (high overlap, any name) ────── #
            if has_suffix and src_vals:
                for tkey, pk_vals in table_key_to_pk_vals.items():
                    tgt_schema, tname = tkey
                    if tkey == src_key:
                        continue
                    key = (src_schema.lower(), src_tl, cname_lower, tgt_schema, tname)
                    if key in declared or key in seen:
                        continue
                    common = src_vals & pk_vals
                    ov = len(common) / len(src_vals) if src_vals else 0.0
                    if ov >= 0.70 and len(common) >= self.min_overlap_n:
                        seen.add(key)
                        results.append(DetectedFK(
                            from_table=src_tn,
                            from_column=cname,
                            to_table=tname,
                            to_column=table_key_to_pk.get(tkey, "id"),
                            confidence=0.75,
                            method="value_only",
                            overlap=round(ov, 3),
                            from_schema=src_schema,
                            to_schema=tgt_schema,
                        ))

        results.sort(key=lambda x: -x.confidence)
        return results

    # ------------------------------------------------------------------ #

    @staticmethod
    def _stem(col: str) -> Tuple[str, bool]:
        m = _FK_SUFFIXES.search(col)
        if m:
            return col[: m.start()].strip('_'), True
        return col, False

    @staticmethod
    def _name_score(stem: str, tname: str) -> float:
        if stem == tname:
            return 1.0
        if tname.endswith(stem) or tname.endswith('_' + stem):
            return 0.95
        if tname == stem + 's' or tname == stem.rstrip('s'):
            return 0.97   # simple plural
        if tname.startswith(stem + '_'):
            ratio = len(stem) / len(tname)
            return 0.90 * ratio + 0.80 * (1 - ratio)
        if tname.startswith(stem):
            ratio = len(stem) / len(tname)
            return 0.88 * ratio + 0.75 * (1 - ratio)
        return SequenceMatcher(None, stem, tname).ratio()


def inject_logical_fks(
    detected: List[DetectedFK],
    entity_graph: EntityGraph,
    min_confidence: float = FK_THRESHOLD,
) -> int:
    """Append detected FKs as ConstraintEntity into entity_graph. Returns count injected."""
    by_schema_name: Dict[Tuple[str, str], Any] = {}
    by_name: Dict[str, List[Any]] = {}
    for table in entity_graph.tables:
        schema = _table_schema(table.id)
        by_schema_name[(schema.lower(), table.name.lower())] = table
        by_name.setdefault(table.name.lower(), []).append(table)

    def _resolve_table(name: str, schema: Optional[str]):
        if schema:
            found = by_schema_name.get((schema.lower(), name.lower()))
            if found:
                return found
        candidates = by_name.get(name.lower(), [])
        return candidates[0] if len(candidates) == 1 else None

    injected = 0
    for fk in detected:
        if fk.confidence < min_confidence:
            continue
        from_table = _resolve_table(fk.from_table, fk.from_schema)
        to_table = _resolve_table(fk.to_table, fk.to_schema)
        if from_table is None or to_table is None:
            continue
        from_schema = _table_schema(from_table.id)
        to_schema = _table_schema(to_table.id)
        con_id = f"logicfk::{from_schema}::{from_table.name}::{fk.from_column}::{to_schema}::{to_table.name}"
        if any(c.id == con_id for c in entity_graph.constraints):
            continue
        entity_graph.constraints.append(ConstraintEntity(
            id=con_id,
            name=f"logic_fk_{from_schema}_{from_table.name}_{fk.from_column}",
            table_id=from_table.id,
            constraint_type="FOREIGN",
            constrained_columns=[fk.from_column],
            referred_schema=to_schema or None,
            referred_table=to_table.name,
            referred_columns=[fk.to_column],
        ))
        injected += 1
    return injected
