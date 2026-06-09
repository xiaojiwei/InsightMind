"""
Table Category Classifier

Classifies each table as one of:
  fact        — records business events/transactions; many outbound FKs; many numeric columns
  dimension   — describes entities (who/what/where); many inbound FKs; mostly string columns
  bridge      — resolves M:N; meaningful outbound AND inbound FKs
  lookup      — small reference/config table; few or no FK relationships; low row count
  unknown     — cannot be determined

Algorithm (scoring):
  1. Name-pattern heuristics (fast, high confidence signals)
  2. FK structure (outbound = fact signals; inbound = dimension signals)
  3. Column type ratio (many numerics → fact; many strings → dimension)
  4. Row count (very small table → lookup)
"""
from __future__ import annotations

import re
from collections import defaultdict
from typing import Dict, List, Tuple

from kg_builder.entities.models import ColumnEntity, ConstraintEntity, EntityGraph, TableEntity

# ── Name heuristic patterns ──────────────────────────────────────────── #
_FACT_NAME_RE = re.compile(
    r'(^|_)(fact|trans|transaction|order|sale|event|log|record|ledger|journal|'
    r'invoice|payment|booking|shipment|return|entry|history|hist|detail|dtl|'
    r'measure_application|application|申请|记录|明细|流水|日志|事务)($|_)',
    re.I,
)
_DIM_NAME_RE = re.compile(
    r'(^|_)(dim|dimension|category|type|status|state|config|setting|dict|'
    r'master|lookup|ref|code|grade|level|class|group|region|area|'
    r'employee|user|customer|product|item|material|supplier|vendor|'
    r'维度|类别|类型|字典|配置|主数据|区域|员工|用户|客户|商品)($|_)',
    re.I,
)
_BRIDGE_NAME_RE = re.compile(
    r'(^|_)(map|mapping|rel|relation|link|assoc|association|xref|cross|'
    r'junction|through|pivot|many|m2m|nm|关联|映射|关系)($|_)',
    re.I,
)


def classify(entity_graph: EntityGraph) -> None:
    """
    Classify every TableEntity in-place, setting table.table_category.
    Also computes fk_out_count and fk_in_count (stored as table attributes
    for later emission as RDF triples).
    """
    # ── Build FK counts ──────────────────────────────────────────────── #
    fk_out: Dict[str, int] = defaultdict(int)   # table_id → # outbound FKs
    fk_in:  Dict[str, int] = defaultdict(int)   # table_id → # inbound FKs

    tname_to_id = {t.name.lower(): t.id for t in entity_graph.tables}

    for con in entity_graph.constraints:
        if con.constraint_type != "FOREIGN" or not con.referred_table:
            continue
        src_id = con.table_id
        tgt_id = tname_to_id.get(con.referred_table.lower())
        if src_id and tgt_id and src_id != tgt_id:
            fk_out[src_id] += 1
            fk_in[tgt_id]  += 1

    # ── Build column type ratios ─────────────────────────────────────── #
    _NUMERIC_RE = re.compile(
        r'int|bigint|smallint|tinyint|decimal|numeric|float|double|'
        r'real|number|money|currency',
        re.I,
    )
    table_col_stats: Dict[str, Tuple[int, int]] = defaultdict(lambda: (0, 0))
    # table_id → (numeric_count, total_count)
    for col in entity_graph.columns:
        if col.is_pk:
            continue
        n, tot = table_col_stats[col.table_id]
        tot += 1
        if _NUMERIC_RE.search(col.data_type):
            n += 1
        table_col_stats[col.table_id] = (n, tot)

    # ── Classify each table ──────────────────────────────────────────── #
    for tbl in entity_graph.tables:
        if tbl.is_view:
            tbl.table_category = "view"
            continue

        tid    = tbl.id
        name   = tbl.name.lower()
        out    = fk_out[tid]
        inp    = fk_in[tid]
        rows   = tbl.row_count or 0
        n_num, n_tot = table_col_stats[tid]
        num_ratio = n_num / n_tot if n_tot > 0 else 0.0

        fact_score = 0
        dim_score  = 0

        # ── Name signals ─────────────────────────────────────────── #
        if _FACT_NAME_RE.search(name):
            fact_score += 3
        if _DIM_NAME_RE.search(name):
            dim_score  += 3
        if _BRIDGE_NAME_RE.search(name):
            # Bridge is a dedicated check — score both sides
            fact_score += 1
            dim_score  += 1

        # ── FK structure ─────────────────────────────────────────── #
        if out >= 5:
            fact_score += 3
        elif out >= 3:
            fact_score += 2
        elif out >= 1:
            fact_score += 1

        if inp >= 5:
            dim_score  += 3
        elif inp >= 3:
            dim_score  += 2
        elif inp >= 1:
            dim_score  += 1

        # ── Column type ratio ─────────────────────────────────────── #
        if num_ratio >= 0.50:
            fact_score += 2
        elif num_ratio >= 0.30:
            fact_score += 1
        elif num_ratio <= 0.10 and n_tot > 2:
            dim_score  += 1

        # ── Row count ─────────────────────────────────────────────── #
        if rows > 10_000:
            fact_score += 1
        elif rows < 200 and rows > 0:
            dim_score  += 1

        # ── Bridge detection ─────────────────────────────────────── #
        # Bridge: meaningful FKs in both directions, column count is low
        if out >= 2 and inp >= 1 and _BRIDGE_NAME_RE.search(name):
            tbl.table_category = "bridge"
            continue

        # ── Lookup detection ─────────────────────────────────────── #
        # Tiny reference table: no outbound FKs, few columns, small
        if out == 0 and inp <= 1 and rows < 100 and n_tot <= 5:
            tbl.table_category = "lookup"
            continue

        # ── Final decision ─────────────────────────────────────── #
        if fact_score > dim_score and fact_score >= 3:
            tbl.table_category = "fact"
        elif dim_score > fact_score and dim_score >= 3:
            tbl.table_category = "dimension"
        elif fact_score == dim_score and fact_score >= 3:
            # Tie — if many outbound FKs lean fact
            tbl.table_category = "fact" if out >= inp else "dimension"
        elif fact_score >= 2 and out >= 3:
            tbl.table_category = "fact"
        elif dim_score >= 2 and inp >= 2:
            tbl.table_category = "dimension"
        else:
            tbl.table_category = "unknown"

    # ── Attach FK counts to table objects for later RDF emission ─────── #
    # We store these as a side-channel via a simple attribute injection
    # (not in the dataclass, used only by rdf_builder)
    for tbl in entity_graph.tables:
        tbl._fk_out_count = fk_out[tbl.id]
        tbl._fk_in_count  = fk_in[tbl.id]
