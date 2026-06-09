"""
P0: Comment Reference Detector
Scan column/table comments for explicit references to other tables.

Examples of patterns detected:
  "关联measure表的id"  →  meas_id references measure
  "外键: user_id"     →  references user table
  "FK to employee"   →  references employee
  "参见category表"   →  references category
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Dict, List, Optional, Set, Tuple

from kg_builder.entities.models import EntityGraph
from kg_builder.relations.fk_detector import DetectedFK

# ── Chinese patterns ────────────────────────────────────────────────── #
_ZH_PATTERNS = [
    # 关联xxx表 / 关联xxx
    re.compile(r'关联\s*[「【\[]?([A-Za-z_]\w*)[」】\]]?\s*表?'),
    # 对应xxx表
    re.compile(r'对应\s*[「【\[]?([A-Za-z_]\w*)[」】\]]?\s*表'),
    # 参见xxx表 / 参考xxx表
    re.compile(r'(?:参见|参考|参照)\s*[「【\[]?([A-Za-z_]\w*)[」】\]]?\s*表?'),
    # 引用xxx表
    re.compile(r'引用\s*[「【\[]?([A-Za-z_]\w*)[」】\]]?\s*表?'),
    # 来自xxx表
    re.compile(r'来自\s*[「【\[]?([A-Za-z_]\w*)[」】\]]?\s*表?'),
    # xxx表的id/编号/主键
    re.compile(r'([A-Za-z_]\w*)\s*表\s*(?:的|中的)?\s*(?:id|ID|主键|编号|标识|pk)'),
    # 外键:xxx / 外键 xxx
    re.compile(r'外键\s*[:：]?\s*[「【\[]?([A-Za-z_]\w*)[」】\]]?'),
]

# ── English patterns ────────────────────────────────────────────────── #
_EN_PATTERNS = [
    re.compile(r'\bFK\s+(?:to\s+)?(\w+)', re.I),
    re.compile(r'\bforeign\s+key\s+(?:to\s+|references?\s+)?(\w+)', re.I),
    re.compile(r'\breferences?\s+(?:table\s+)?(\w+)', re.I),
    re.compile(r'\bsee\s+(?:table\s+)?(\w+)', re.I),
    re.compile(r'\blinks?\s+to\s+(\w+)', re.I),
    re.compile(r'\bbelongs?\s+to\s+(\w+)', re.I),
]

# Words to skip even if they match a table name (too common to be refs)
_SKIP_WORDS = {
    'the', 'id', 'no', 'key', 'code', 'type', 'name', 'date', 'time',
    'user', 'data', 'info', 'list', 'all', 'this', 'that', 'value',
    '表', '列', '字段', '主键', '外键',
}


@dataclass
class CommentRef:
    from_table:  str
    from_column: Optional[str]   # None → table-level comment
    to_table:    str
    confidence:  float
    evidence:    str             # matched text snippet


class CommentRefDetector:
    """
    Scan column and table comments for explicit table references.
    Returns CommentRef objects convertible to DetectedFK.
    """

    def detect(self, entity_graph: EntityGraph) -> List[CommentRef]:
        tnames_lower: Dict[str, str] = {
            t.name.lower(): t.name for t in entity_graph.tables
        }
        results: List[CommentRef] = []
        seen: Set[Tuple] = set()

        # ── Column-level comments ──────────────────────────────────────── #
        for col in entity_graph.columns:
            if not col.comment:
                continue
            src_tn  = col.table_id.split("::")[-1]
            src_low = src_tn.lower()

            for to_name, snippet in self._scan(col.comment, tnames_lower, src_low):
                key = (src_low, col.name.lower(), to_name.lower())
                if key in seen:
                    continue
                seen.add(key)
                results.append(CommentRef(
                    from_table=src_tn,
                    from_column=col.name,
                    to_table=to_name,
                    confidence=0.90,
                    evidence=snippet,
                ))

        # ── Table-level comments ───────────────────────────────────────── #
        for tbl in entity_graph.tables:
            if not tbl.comment:
                continue
            src_low = tbl.name.lower()
            for to_name, snippet in self._scan(tbl.comment, tnames_lower, src_low):
                key = (src_low, "__table__", to_name.lower())
                if key in seen:
                    continue
                seen.add(key)
                results.append(CommentRef(
                    from_table=tbl.name,
                    from_column=None,
                    to_table=to_name,
                    confidence=0.80,
                    evidence=snippet,
                ))

        return results

    # ------------------------------------------------------------------ #

    @staticmethod
    def _scan(
        comment: str,
        tnames_lower: Dict[str, str],
        src_lower: str,
    ) -> List[Tuple[str, str]]:
        found: List[Tuple[str, str]] = []
        seen_targets: Set[str] = set()

        for pat in _ZH_PATTERNS + _EN_PATTERNS:
            for m in pat.finditer(comment):
                cand = m.group(1).strip().lower()
                if cand in _SKIP_WORDS or cand == src_lower:
                    continue
                if cand in tnames_lower and cand not in seen_targets:
                    seen_targets.add(cand)
                    found.append((tnames_lower[cand], m.group(0)))

        return found


# ── Conversion helper ────────────────────────────────────────────────── #

def comment_refs_to_detected_fks(
    refs: List[CommentRef],
    entity_graph: EntityGraph,
) -> List[DetectedFK]:
    """
    Convert column-level CommentRefs to DetectedFK objects
    so they can be injected via inject_logical_fks().
    Table-level refs (from_column=None) are skipped — no column to anchor.
    """
    tname_to_pk: Dict[str, str] = {}
    for con in entity_graph.constraints:
        if con.constraint_type == "PRIMARY" and len(con.constrained_columns) == 1:
            tn = con.table_id.split("::")[-1].lower()
            tname_to_pk[tn] = con.constrained_columns[0]

    detected: List[DetectedFK] = []
    for ref in refs:
        if ref.from_column is None:
            continue
        pk_col = tname_to_pk.get(ref.to_table.lower(), "id")
        detected.append(DetectedFK(
            from_table=ref.from_table,
            from_column=ref.from_column,
            to_table=ref.to_table,
            to_column=pk_col,
            confidence=ref.confidence,
            method="comment_ref",
            overlap=0.0,
        ))
    return detected
