"""
P1: Enum Value Alignment Detector

Finds columns with small value domains (cardinality ≤ threshold) that share
identical or overlapping value sets across different tables.

Examples:
  order.status  = {0, 1, 2, 3}
  task.status   = {0, 1, 2, 3}   → exact match  → confidence 0.90
  item.state    = {0, 1, 2}      → subset        → confidence 0.80

Output: Relations of type "sharedEnum" (Column ↔ Column, symmetric).
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Set, Tuple

from kg_builder.entities.models import ColumnEntity, EntityGraph
from kg_builder.relations.explicit import Relation

# ── Config ──────────────────────────────────────────────────────────── #
MAX_ENUM_CARDINALITY = 20   # columns with more distinct values are not enums
MIN_SHARED_VALUES    = 2    # must share at least this many values
MIN_OVERLAP_RATIO    = 0.70 # partial overlap threshold

# Column names that are almost always integers but not enums
_SKIP_COL_PATTERNS = {
    'id', 'pk', 'uuid', 'created_at', 'updated_at', 'create_time',
    'update_time', 'created_by', 'updated_by', 'sort', 'order_num',
    'seq', 'row_num', 'version',
}

REL_SHARED_ENUM = "sharedEnum"


@dataclass
class EnumGroup:
    """A cluster of columns sharing the same enum domain."""
    value_set:   frozenset
    columns:     List[ColumnEntity] = field(default_factory=list)


@dataclass
class EnumAlignment:
    col_a_id:      str
    col_b_id:      str
    col_a_name:    str
    col_b_name:    str
    table_a:       str
    table_b:       str
    shared_values: frozenset
    confidence:    float
    relation_type: str   # "exact_match" | "subset" | "partial_overlap"


class EnumDetector:

    def __init__(self, max_cardinality: int = MAX_ENUM_CARDINALITY) -> None:
        self.max_cardinality = max_cardinality

    # ------------------------------------------------------------------ #

    def detect(self, entity_graph: EntityGraph) -> List[EnumAlignment]:
        candidates = self._collect_candidates(entity_graph.columns)
        if len(candidates) < 2:
            return []

        results: List[EnumAlignment] = []
        seen: Set[Tuple[str, str]] = set()

        for i, col_a in enumerate(candidates):
            vals_a = frozenset(str(v) for v in col_a.top_values)
            if len(vals_a) < MIN_SHARED_VALUES:
                continue

            for col_b in candidates[i + 1:]:
                if col_a.table_id == col_b.table_id:
                    continue

                vals_b = frozenset(str(v) for v in col_b.top_values)
                if len(vals_b) < MIN_SHARED_VALUES:
                    continue

                key = (min(col_a.id, col_b.id), max(col_a.id, col_b.id))
                if key in seen:
                    continue

                shared = vals_a & vals_b
                if len(shared) < MIN_SHARED_VALUES:
                    continue

                # ── Classify the overlap type ──────────────────────── #
                if vals_a == vals_b:
                    confidence = 0.90
                    rel_type   = "exact_match"
                elif vals_a <= vals_b or vals_b <= vals_a:
                    overlap    = len(shared) / min(len(vals_a), len(vals_b))
                    if overlap < 0.80:
                        continue
                    confidence = 0.80
                    rel_type   = "subset"
                else:
                    overlap = len(shared) / max(len(vals_a), len(vals_b))
                    if overlap < MIN_OVERLAP_RATIO:
                        continue
                    confidence = round(0.65 + 0.15 * overlap, 3)
                    rel_type   = "partial_overlap"

                # Boost confidence when column names are identical
                if col_a.name.lower() == col_b.name.lower():
                    confidence = min(confidence + 0.05, 0.95)

                seen.add(key)
                results.append(EnumAlignment(
                    col_a_id=col_a.id,
                    col_b_id=col_b.id,
                    col_a_name=col_a.name,
                    col_b_name=col_b.name,
                    table_a=col_a.table_id.split("::")[-1],
                    table_b=col_b.table_id.split("::")[-1],
                    shared_values=shared,
                    confidence=confidence,
                    relation_type=rel_type,
                ))

        results.sort(key=lambda x: -x.confidence)
        return results

    # ------------------------------------------------------------------ #

    def _collect_candidates(self, columns: List[ColumnEntity]) -> List[ColumnEntity]:
        out = []
        for col in columns:
            if col.is_pk:
                continue
            if col.name.lower() in _SKIP_COL_PATTERNS:
                continue
            if not (2 <= col.cardinality <= self.max_cardinality):
                continue
            if len(col.top_values) < MIN_SHARED_VALUES:
                continue
            out.append(col)
        return out


# ── Conversion helper ────────────────────────────────────────────────── #

def enum_alignments_to_relations(alignments: List[EnumAlignment]) -> List[Relation]:
    """Convert EnumAlignment objects to symmetric Relation pairs."""
    relations: List[Relation] = []
    for aln in alignments:
        props = {
            "relation_type":  aln.relation_type,
            "shared_values":  sorted(aln.shared_values),
            "table_a":        aln.table_a,
            "table_b":        aln.table_b,
        }
        relations.append(Relation(
            subject_id=aln.col_a_id,
            predicate=REL_SHARED_ENUM,
            object_id=aln.col_b_id,
            confidence=aln.confidence,
            properties=props,
        ))
        relations.append(Relation(
            subject_id=aln.col_b_id,
            predicate=REL_SHARED_ENUM,
            object_id=aln.col_a_id,
            confidence=aln.confidence,
            properties=props,
        ))
    return relations
