"""
MetadataSummaryExtractor
Converts an rdflib data-source KG into a compact, LLM-friendly text summary
that describes databases, tables (with warehouse category), columns, FKs, and
sample enum values.
"""
from __future__ import annotations

from collections import defaultdict
from typing import Optional

from rdflib import Graph, RDF, RDFS, URIRef
from kg_builder.ontology.owl_schema import DB


class MetadataSummaryExtractor:
    """Extract a compact text summary from a data-source RDF graph."""

    # Maximum number of columns to emit per table. None means keep all columns;
    # batching happens later in BusinessKGBuilder, so table metadata should not
    # be discarded here.
    _MAX_COLS_PER_TABLE = None
    # Maximum number of top-values to show per enum column
    _MAX_TOP_VALUES = 4           # 降低：6→4
    # Hard cap removed: schema-scoped business graph generation must keep all
    # tables from the selected source schema.
    _MAX_TABLES = None

    def __init__(self, g: Graph, domain_hint: str = "", target_schema: str = "") -> None:
        self._g = g
        self._domain_hint = domain_hint.lower()
        self._target_schema = (target_schema or "").strip()

    # ------------------------------------------------------------------ #

    def _table_score(self, tbl_uri) -> int:
        """Score a table by how relevant it is to the domain hint."""
        if not self._domain_hint:
            return 0
        g = self._g
        text = " ".join([
            str(g.value(tbl_uri, DB.tableName) or ""),
            str(g.value(tbl_uri, DB.comment) or ""),
            str(self._zh_label(tbl_uri) or ""),
        ]).lower()
        return sum(1 for word in self._domain_hint.split() if word and word in text)

    def extract(self) -> str:
        g = self._g
        lines: list[str] = []

        # ── Databases ──────────────────────────────────────────────────── #
        db_uris = list(g.subjects(RDF.type, DB.Database))
        db_info: dict[str, dict] = {}
        for db_uri in db_uris:
            name     = str(g.value(db_uri, DB.name)     or "unknown")
            db_type  = str(g.value(db_uri, DB.dbType)   or "")
            host     = str(g.value(db_uri, DB.host)     or "")
            database = str(g.value(db_uri, DB.database) or "")
            port     = str(g.value(db_uri, DB.port)     or "")
            username = str(g.value(db_uri, DB.username) or "")
            password = str(g.value(db_uri, DB.password) or "")
            db_info[str(db_uri)] = {"name": name, "type": db_type, "host": host,
                                    "database": database, "port": port,
                                    "username": username, "password": password}
            if database == "*":
                schema_label = "多库扫描(见每张表的 schemaName)"
            else:
                schema_label = database or name  # 实际 schema 名优先
            port_str = f":{port}" if port else ""
            lines.append(f"# 数据库配置: 标签={name}  类型={db_type}  主机={host}{port_str}"
                         f"  账号={username}  密码={password}  实际库名(schemaName)={schema_label}")

        schema_names = sorted({
            str(g.value(schema_uri, DB.name) or g.value(schema_uri, RDFS.label) or "")
            for schema_uri in g.subjects(RDF.type, DB.Schema)
        } - {""})
        if schema_names:
            lines.append(f"# 可用业务库(schemaName): {', '.join(schema_names)}")
            lines.append("# ⚠ ind:schemaName 必须使用每张表所属的真实业务库；多库扫描时不要使用 \"*\"")
        if self._target_schema:
            lines.append(f"# 本次业务图谱仅使用业务库(schemaName): {self._target_schema}")

        # ── Table count summary ─────────────────────────────────────────── #
        all_tables = [
            t for t in g.subjects(RDF.type, DB.Table)
            if self._include_table(t)
        ]
        cat_counts: dict[str, int] = defaultdict(int)
        for t in all_tables:
            cat = str(g.value(t, DB.tableCategory) or "unknown")
            cat_counts[cat] += 1

        lines.append(
            f"# 表总数: {len(all_tables)}  |  "
            + "  ".join(f"{k}={v}" for k, v in sorted(cat_counts.items()))
        )
        lines.append("")

        # ── Build column lookup: table_uri → list[col_uri] ─────────────── #
        table_cols: dict[str, list] = defaultdict(list)
        for col_uri in g.subjects(RDF.type, DB.Column):
            # Find parent table via containsColumn (reverse) or belongsToTable
            parent = g.value(col_uri, DB.belongsToTable)
            if parent is None:
                # try reverse of containsColumn
                for t in g.subjects(DB.containsColumn, col_uri):
                    parent = t
                    break
            if parent:
                table_cols[str(parent)].append(col_uri)

        # ── Build FK label map: col_uri → "→ referred_table.name" ──────── #
        fk_labels: dict[str, str] = {}
        for con_uri in g.subjects(RDF.type, DB.Constraint):
            if str(g.value(con_uri, DB.name) or "") != "FOREIGN":
                continue
            ref_tbl = g.value(con_uri, DB.references)
            ref_name = str(g.value(ref_tbl, DB.tableName) or g.value(ref_tbl, DB.name) or "") if ref_tbl else ""
            for col_uri in g.objects(con_uri, DB.coversColumn):
                fk_labels[str(col_uri)] = f"FK(→{ref_name})" if ref_name else "FK"

        # ── Sort tables (fact first, then by domain score desc) ────────── #
        # Build a priority-sorted list: fact first, then by domain score desc
        CATEGORY_PRIORITY = {"fact": 0, "dimension": 1, "bridge": 2,
                              "lookup": 3, "view": 4, "unknown": 5}
        all_tables_sorted = sorted(
            all_tables,
            key=lambda t: (
                CATEGORY_PRIORITY.get(str(g.value(t, DB.tableCategory) or "unknown"), 5),
                -self._table_score(t),
                str(g.value(t, DB.tableName) or ""),
            ),
        )
        if self._MAX_TABLES and len(all_tables_sorted) > self._MAX_TABLES:
            dropped = len(all_tables_sorted) - self._MAX_TABLES
            lines.append(
                f"# ⚠ 表总数 {len(all_tables_sorted)} 超过上限 {self._MAX_TABLES}，"
                f"已按相关性保留 {self._MAX_TABLES} 张，忽略 {dropped} 张低相关性表"
            )
            all_tables_sorted = all_tables_sorted[: self._MAX_TABLES]

        # ── Emit tables grouped by category ────────────────────────────── #
        CATEGORY_ORDER = ["fact", "dimension", "bridge", "lookup", "view", "unknown"]
        CATEGORY_LABELS = {
            "fact":      "事实表 (业务事件/交易流水)",
            "dimension": "维度表 (业务实体/主数据)",
            "bridge":    "桥接表 (M:N关联)",
            "lookup":    "查找表 (参数/枚举/配置)",
            "view":      "视图",
            "unknown":   "分类未知",
        }

        # Group tables by category (only the selected tables)
        by_cat: dict[str, list] = defaultdict(list)
        for t in all_tables_sorted:
            cat = str(g.value(t, DB.tableCategory) or "unknown")
            by_cat[cat].append(t)

        for cat in CATEGORY_ORDER:
            tables_in_cat = by_cat.get(cat, [])
            if not tables_in_cat:
                continue
            lines.append(f"## {CATEGORY_LABELS.get(cat, cat)}")
            lines.append("")

            for tbl_uri in sorted(tables_in_cat,
                                   key=lambda x: str(g.value(x, DB.tableName) or "")):
                tbl_name  = str(g.value(tbl_uri, DB.tableName) or g.value(tbl_uri, DB.name) or "")
                tbl_schema = self._table_schema(tbl_uri)
                tbl_comment = g.value(tbl_uri, DB.comment)
                row_count   = g.value(tbl_uri, DB.rowCount)
                fk_out      = g.value(tbl_uri, DB.fkOutCount)
                fk_in       = g.value(tbl_uri, DB.fkInCount)
                zh_label    = self._zh_label(tbl_uri)

                meta_parts = []
                if tbl_schema:
                    meta_parts.append(f"schemaName={tbl_schema}")
                if zh_label:
                    meta_parts.append(zh_label)
                if tbl_comment and str(tbl_comment) != zh_label:
                    meta_parts.append(str(tbl_comment))
                if row_count is not None:
                    meta_parts.append(f"行数≈{int(row_count)}")
                if fk_out:
                    meta_parts.append(f"FK出:{int(fk_out)}")
                if fk_in:
                    meta_parts.append(f"FK入:{int(fk_in)}")

                header = f"### {tbl_name}"
                if meta_parts:
                    header += f" — {' | '.join(meta_parts)}"
                lines.append(header)

                # Columns
                cols = table_cols.get(str(tbl_uri), [])
                # Sort: PK first, then FK, then the rest
                def _col_sort_key(cu):
                    is_pk = str(g.value(cu, DB.isPrimaryKey) or "false").lower() == "true"
                    is_fk = str(cu) in fk_labels
                    has_comment = g.value(cu, DB.comment) is not None
                    return (not is_pk, not is_fk, not has_comment)
                cols = sorted(cols, key=_col_sort_key)
                if self._MAX_COLS_PER_TABLE:
                    cols = cols[:self._MAX_COLS_PER_TABLE]

                for col_uri in cols:
                    col_name  = str(g.value(col_uri, DB.name) or "")
                    col_type  = str(g.value(col_uri, DB.columnType) or "")
                    is_pk     = str(g.value(col_uri, DB.isPrimaryKey) or "false").lower() == "true"
                    nullable  = str(g.value(col_uri, DB.isNullable) or "true").lower() == "true"
                    comment   = g.value(col_uri, DB.comment)
                    zh_col    = self._zh_label(col_uri)
                    fk_hint   = fk_labels.get(str(col_uri), "")

                    flags = []
                    if is_pk:  flags.append("PK")
                    if fk_hint: flags.append(fk_hint)
                    if not nullable: flags.append("NOT NULL")

                    top_vals = [str(v) for v in g.objects(col_uri, DB.topValue)]
                    top_str = ""
                    if top_vals:
                        top_str = f" [枚举值: {', '.join(top_vals[:self._MAX_TOP_VALUES])}]"

                    desc_parts = []
                    if zh_col:
                        desc_parts.append(zh_col)
                    elif comment:
                        desc_parts.append(str(comment))
                    desc = (" — " + " | ".join(desc_parts)) if desc_parts else ""

                    flag_str = f" [{', '.join(flags)}]" if flags else ""
                    lines.append(f"  - {col_name}: {col_type}{flag_str}{top_str}{desc}")

                lines.append("")

        # ── FK relationship summary ─────────────────────────────────────── #
        fk_edges: list[str] = []
        for col_uri in g.subjects(RDF.type, DB.Column):
            for ref_tbl_uri in g.objects(col_uri, DB.references):
                col_name   = str(g.value(col_uri, DB.name) or "")
                # find parent table name
                parent_tbl = g.value(col_uri, DB.belongsToTable)
                if parent_tbl is None:
                    for t in g.subjects(DB.containsColumn, col_uri):
                        parent_tbl = t
                        break
                if not self._include_table(parent_tbl):
                    continue
                src = str(g.value(parent_tbl, DB.tableName) or "") if parent_tbl else "?"
                src_schema = self._table_schema(parent_tbl) if parent_tbl else ""
                dst = str(g.value(ref_tbl_uri, DB.tableName) or "")
                dst_schema = self._table_schema(ref_tbl_uri) if ref_tbl_uri else ""
                src_label = f"{src_schema}.{src}" if src_schema else src
                dst_label = f"{dst_schema}.{dst}" if dst_schema else dst
                fk_edges.append(f"  {src_label}.{col_name} → {dst_label}")

        if fk_edges:
            lines.append("## 外键关系")
            lines.extend(sorted(set(fk_edges)))
            lines.append("")

        return "\n".join(lines)

    # ------------------------------------------------------------------ #

    def _zh_label(self, uri) -> Optional[str]:
        g = self._g
        for lbl in g.objects(uri, RDFS.label):
            if getattr(lbl, "language", None) == "zh":
                return str(lbl)
        import re
        zh_re = re.compile(r"[\u4e00-\u9fff]")
        for lbl in g.objects(uri, RDFS.label):
            if zh_re.search(str(lbl)):
                return str(lbl)
        return None

    def _table_schema(self, tbl_uri) -> str:
        if tbl_uri is None:
            return ""
        schema_uri = self._g.value(tbl_uri, DB.belongsToSchema)
        if schema_uri is None:
            for candidate in self._g.subjects(DB.containsTable, tbl_uri):
                schema_uri = candidate
                break
        if schema_uri is None:
            return ""
        return str(self._g.value(schema_uri, DB.name) or self._g.value(schema_uri, RDFS.label) or "")

    def _include_table(self, tbl_uri) -> bool:
        if tbl_uri is None:
            return False
        if not self._target_schema:
            return True
        return self._table_schema(tbl_uri).lower() == self._target_schema.lower()
