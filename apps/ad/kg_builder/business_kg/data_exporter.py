"""
IndicatorDataExporter
Directly reads MySQL indicator tables and generates Turtle individuals
following the ind: ontology spec. No LLM involved — 100% deterministic.

Tables covered:
  measure, dimension, category, hierarchy, level,
  measure_application, dimension_application,
  dimension_dimtable_connect, dw_table, dw_column,
  measure_natural_date_mapping

Schema notes (indicator_dump):
  - dw_table.type         → table type (0=fact, 1=dim); may be NULL
  - measure_application   → NO is_del column; use `available` only
  - measure               → NO meas_type column; derived from application.apply_type
  - measure_natural_date_mapping → columns: meas_id, natural_dim_id, target_dim_id, dw_table_id
  - dimension_dimtable_connect   → NO dw_table_id; uses schema_name + dim_table_name
"""
from __future__ import annotations

import json
import re
from typing import Any, Callable, Optional


# ── Aggregation function name normalization ────────────────────────────────── #

_AGG_FUNC_MAP = {
    "distinct_count": "COUNT_DISTINCT",
    "count_distinct": "COUNT_DISTINCT",
    "count":          "COUNT",
    "sum":            "SUM",
    "avg":            "AVG",
    "average":        "AVG",
    "max":            "MAX",
    "min":            "MIN",
}


def _extract_agg_func(expression: Any) -> str:
    """
    Extract the primary aggregation function from a measure_application.expression
    JSON string.  Returns a normalized name like 'SUM', 'COUNT', 'AVG', etc.
    Returns '' if nothing can be determined.
    """
    if not expression:
        return ""
    expr_str = str(expression)
    # Expression is a JSON array of AST nodes; find first "operator" value
    ops = re.findall(r'"operator"\s*:\s*"([^"]+)"', expr_str)
    if not ops:
        return ""
    raw = ops[0].lower()
    return _AGG_FUNC_MAP.get(raw, raw.upper())


# ── Turtle helpers ─────────────────────────────────────────────────────────── #

def _esc(s: Any) -> str:
    """Escape a value for use inside a Turtle string literal."""
    if s is None:
        return ""
    text = str(s)
    text = text.replace("\\", "\\\\")
    text = text.replace('"', '\\"')
    text = text.replace("\n", "\\n")
    text = text.replace("\r", "\\r")
    text = text.replace("\t", "\\t")
    # Strip control characters invalid in Turtle strings
    text = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '', text)
    return text


def _str_triple(subj: str, pred: str, val: Any) -> str:
    if val is None or str(val).strip() == "":
        return ""
    return f'    {pred} "{_esc(val)}" ;\n'


def _int_triple(subj: str, pred: str, val: Any) -> str:
    if val is None:
        return ""
    try:
        return f"    {pred} {int(val)} ;\n"
    except (ValueError, TypeError):
        return ""


def _bool_triple(subj: str, pred: str, val: Any) -> str:
    if val is None:
        return ""
    if isinstance(val, str):
        low = val.strip().lower()
        if low in {"1", "true", "t", "yes", "y"}:
            return f"    {pred} true ;\n"
        if low in {"0", "false", "f", "no", "n"}:
            return f"    {pred} false ;\n"
        return ""
    return f"    {pred} {'true' if bool(val) else 'false'} ;\n"


def _ref_triple(pred: str, uri: str) -> str:
    return f"    {pred} {uri} ;\n"


# ── Exporter class ─────────────────────────────────────────────────────────── #

class IndicatorDataExporter:
    """
    Export indicator platform data from MySQL to Turtle individuals.
    """

    def __init__(
        self,
        host: str,
        port: int,
        database: str,
        username: str,
        password: str,
        log_cb: Optional[Callable[[str], None]] = None,
    ) -> None:
        self._host     = host
        self._port     = port
        self._database = database
        self._username = username
        self._password = password
        self._log      = log_cb or (lambda m: None)
        # 生成连接实例 URI 标识（host 简称 + database）
        host_short = re.sub(r'[^a-zA-Z0-9]', '_', host)
        db_short   = re.sub(r'[^a-zA-Z0-9]', '_', database)
        self._conn_id = f"mysql_{host_short}_{db_short}"

    def export(self) -> str:
        """Return a Turtle string containing all individuals (no prefix declarations)."""
        import pymysql

        conn = pymysql.connect(
            host=self._host,
            port=self._port,
            database=self._database,
            user=self._username,
            password=self._password,
            charset="utf8mb4",
            connect_timeout=10,
        )
        try:
            parts: list[str] = []
            cur = conn.cursor(pymysql.cursors.DictCursor)

            cur.execute("SHOW TABLES")
            tables = {list(r.values())[0] for r in cur.fetchall()}
            self._log(f"数据库共 {len(tables)} 张表，开始提取指标/维度数据…")

            # ── 基础数据 ──────────────────────────────────────────────── #
            parts.append(self._export_connection())
            parts.append(self._export_categories(cur, tables))
            parts.append(self._export_hierarchies(cur, tables))
            parts.append(self._export_dwtables(cur, tables))
            parts.append(self._export_dwcolumns(cur, tables))
            parts.append(self._export_dimensions(cur, tables))

            # ── 指标（先生成基础节点，后面补 measType / functionType）── #
            meas_base = self._export_measures(cur, tables)
            parts.append(meas_base)

            # ── 指标应用（含 aggFunction 提取）────────────────────────── #
            mapp_part, mapp_agg_map = self._export_measure_applications(cur, tables)
            parts.append(mapp_part)

            # ── 维度应用 ──────────────────────────────────────────────── #
            parts.append(self._export_dimension_applications(cur, tables))

            # ── 维表关联 ──────────────────────────────────────────────── #
            parts.append(self._export_dimtable_connects(cur, tables))

            # ── 公共维度映射（NaturalDimMapping，新结构）────────────── #
            parts.append(self._build_natural_dim_mappings(cur, tables))

            # ── 指标→来源表 / 指标→可用维度（直连）────────────────────── #
            parts.append(self._export_measure_source_tables(cur, tables))
            parts.append(self._export_measure_available_dimensions(cur, tables))

            # ── 补充 measType / functionType（从应用推导）────────────── #
            parts.append(self._derive_measure_metadata(
                cur, tables, mapp_agg_map))

            result = "\n".join(p for p in parts if p.strip())
            self._log(f"✓ ETL 完成，生成 Turtle 长度: {len(result)} 字符")
            return result
        finally:
            conn.close()

    # ------------------------------------------------------------------ #
    # Internal helpers
    # ------------------------------------------------------------------ #

    def _fetchall(self, cur, sql: str, args=None) -> list[dict]:
        cur.execute(sql, args or ())
        return cur.fetchall() or []

    def _table_exists(self, tables: set, name: str) -> bool:
        return name.lower() in {t.lower() for t in tables}

    def _table_columns(self, cur, table_name: str) -> set[str]:
        try:
            cur.execute(f"SHOW COLUMNS FROM `{table_name}`")
            return {str(r.get("Field") or "").lower() for r in cur.fetchall() or []}
        except Exception:
            return set()

    @staticmethod
    def _first_existing(cols: set[str], *candidates: str) -> Optional[str]:
        for col in candidates:
            if col.lower() in cols:
                return col
        return None

    # ------------------------------------------------------------------ #
    # DataConnection
    # ------------------------------------------------------------------ #

    def _export_connection(self) -> str:
        """Generate a single DataConnection instance for the ETL connection."""
        uri = f"inst:conn_{self._conn_id}"
        block  = f"# ── 数据库连接 ──\n"
        block += f"{uri} a ind:DataConnection ;\n"
        block += f'    ind:dbType     "mysql" ;\n'
        block += f'    ind:host       "{_esc(self._host)}" ;\n'
        block += f'    ind:port       {self._port} ;\n'
        block += f'    ind:dbUser     "{_esc(self._username)}" ;\n'
        block += f'    ind:dbPassword "{_esc(self._password)}" ;\n'
        block += f'    ind:dbName     "{_esc(self._database)}" .\n'
        return block

    # ------------------------------------------------------------------ #
    # category
    # ------------------------------------------------------------------ #

    def _export_categories(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "category"):
            return ""
        try:
            rows = self._fetchall(cur, """
                SELECT id, parent_id, name, code, description, sequence
                FROM category
                WHERE COALESCE(is_delete, 0) = 0
                ORDER BY id
            """)
        except Exception:
            rows = self._fetchall(cur, """
                SELECT id, parent_id, name, code, description, sequence
                FROM category ORDER BY id
            """)
        if not rows:
            return ""
        self._log(f"  category: {len(rows)} 条")
        out = [f"# ── 分类 ({len(rows)}) ──\n"]
        for r in rows:
            rid = r["id"]
            uri = f"cat:{rid}"
            block = f"{uri} a ind:Category ;\n"
            block += _int_triple(uri, "ind:localId", rid)
            block += _str_triple(uri, "ind:code", r.get("code"))
            block += _str_triple(uri, "ind:categoryName", r.get("name"))
            block += _str_triple(uri, "ind:description", r.get("description"))
            block += _int_triple(uri, "ind:sequence", r.get("sequence"))
            pid = r.get("parent_id")
            if pid:
                try:
                    if int(pid) > 0:
                        block += _ref_triple("ind:parentCategory", f"cat:{pid}")
                except (ValueError, TypeError):
                    pass
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # hierarchy + level
    # ------------------------------------------------------------------ #

    def _export_hierarchies(self, cur, tables: set) -> str:
        out = []
        if self._table_exists(tables, "hierarchy"):
            rows = self._fetchall(cur, "SELECT id, code, name FROM hierarchy ORDER BY id")
            if rows:
                self._log(f"  hierarchy: {len(rows)} 条")
                out.append(f"# ── 维度层次 ({len(rows)}) ──\n")
                for r in rows:
                    uri = f"hier:h{r['id']}"
                    block = f"{uri} a ind:Hierarchy ;\n"
                    block += _int_triple(uri, "ind:localId", r["id"])
                    block += _str_triple(uri, "ind:code", r.get("code"))
                    block += _str_triple(uri, "ind:cnName", r.get("name"))
                    block = block.rstrip(" ;\n") + " .\n"
                    out.append(block)

        if self._table_exists(tables, "level"):
            rows = self._fetchall(cur, """
                SELECT id, code, sequence, hierarchy_id, dim_id
                FROM level ORDER BY hierarchy_id, sequence
            """)
            if rows:
                self._log(f"  level: {len(rows)} 条")
                out.append(f"# ── 维度级别 ({len(rows)}) ──\n")
                for r in rows:
                    # Use row id for URI uniqueness (dim_id may repeat across hierarchies)
                    uri = f"hier:level_{r['id']}"
                    dim_id = r.get("dim_id")
                    block = f"{uri} a ind:Level ;\n"
                    block += _int_triple(uri, "ind:localId", r["id"])
                    block += _str_triple(uri, "ind:levelCode", r.get("code"))
                    block += _int_triple(uri, "ind:levelSequence", r.get("sequence"))
                    if r.get("hierarchy_id"):
                        block += _ref_triple("ind:belongsToHierarchy", f"hier:h{r['hierarchy_id']}")
                    if dim_id:
                        block += _ref_triple("ind:forDimension", f"dim:{dim_id}")
                    block = block.rstrip(" ;\n") + " .\n"
                    out.append(block)
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # dw_table
    # Fix: column is `type` (not `table_type`); no `is_delete` column
    # ------------------------------------------------------------------ #

    def _export_dwtables(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "dw_table"):
            return ""
        # `type` is the actual column name for table type; alias to table_type
        rows = self._fetchall(cur, """
            SELECT id, schema_name, table_name, cn_name,
                   type         AS table_type,
                   source_type,
                   has_dt,
                   fact_table_type,
                   aggregation_table,
                   online,
                   description,
                   table_detail_name
            FROM dw_table
            ORDER BY id
        """)
        if not rows:
            return ""
        self._log(f"  dw_table: {len(rows)} 条")
        out = [f"# ── 数仓物理表 ({len(rows)}) ──\n"]
        for r in rows:
            uri = f"tbl:{r['id']}"
            block = f"{uri} a ind:DwTable ;\n"
            block += _int_triple(uri, "ind:localId", r["id"])
            block += _str_triple(uri, "ind:schemaName", r.get("schema_name"))
            block += _str_triple(uri, "ind:tableName", r.get("table_name"))
            block += _str_triple(uri, "ind:cnName", r.get("cn_name"))
            block += _str_triple(uri, "ind:description", r.get("description"))
            block += _str_triple(uri, "ind:tableDetailName", r.get("table_detail_name"))
            # table_type may be NULL; default to 0 (事实表) when used as measure source
            tbl_type = r.get("table_type")
            block += _int_triple(uri, "ind:tableType", tbl_type if tbl_type is not None else 0)
            block += _int_triple(uri, "ind:sourceType", r.get("source_type"))
            block += _int_triple(uri, "ind:hasDt", r.get("has_dt"))
            block += _int_triple(uri, "ind:factTableType", r.get("fact_table_type"))
            block += _int_triple(uri, "ind:aggregationTable", r.get("aggregation_table"))
            block += _int_triple(uri, "ind:online", r.get("online", 1))
            block += f'    ind:hasConnection inst:conn_{self._conn_id} ;\n'
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # dw_column
    # ------------------------------------------------------------------ #

    def _export_dwcolumns(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "dw_column"):
            return ""

        cols = self._table_columns(cur, "dw_column")
        if not cols:
            return ""

        table_id_col = self._first_existing(cols, "dw_table_id", "table_id")
        column_name_col = self._first_existing(cols, "column_name", "name", "col_name")
        if not table_id_col or not column_name_col:
            self._log("  dw_column: 缺少 dw_table_id/table_id 或 column_name/name，跳过字段字典")
            return ""

        id_col = self._first_existing(cols, "id")
        cn_col = self._first_existing(cols, "cn_name", "column_cn_name", "comment", "column_comment")
        type_col = self._first_existing(cols, "data_type", "column_type", "type")
        comment_col = self._first_existing(cols, "column_comment", "comment", "description")
        pk_col = self._first_existing(cols, "is_primary_key", "is_pk", "primary_key")
        nullable_col = self._first_existing(cols, "is_nullable", "nullable")
        ordinal_col = self._first_existing(cols, "ordinal_position", "column_order", "sequence", "sort")

        select_exprs = []
        for alias, col in [
            ("id", id_col),
            ("dw_table_id", table_id_col),
            ("column_name", column_name_col),
            ("cn_name", cn_col),
            ("column_type", type_col),
            ("column_comment", comment_col),
            ("is_primary_key", pk_col),
            ("is_nullable", nullable_col),
            ("ordinal_position", ordinal_col),
        ]:
            if col:
                select_exprs.append(f"`{col}` AS `{alias}`")
            else:
                select_exprs.append(f"NULL AS `{alias}`")

        order_col = id_col or ordinal_col or column_name_col
        rows = self._fetchall(cur, f"""
            SELECT {", ".join(select_exprs)}
            FROM dw_column
            ORDER BY `{order_col}`
        """)
        if not rows:
            return ""

        self._log(f"  dw_column: {len(rows)} 条")
        out = [f"# ── 数仓物理字段 ({len(rows)}) ──\n"]
        for r in rows:
            rid = r.get("id")
            table_id = r.get("dw_table_id")
            col_name = r.get("column_name")
            if not table_id or not col_name:
                continue
            if rid:
                uri = f"col:{rid}"
            else:
                safe_col = re.sub(r"[^a-zA-Z0-9_]", "_", str(col_name))
                uri = f"col:t{table_id}_{safe_col}"
            block = f"{uri} a ind:DwColumn ;\n"
            block += _int_triple(uri, "ind:localId", rid)
            block += _str_triple(uri, "ind:columnName", col_name)
            block += _str_triple(uri, "ind:columnType", r.get("column_type") or "UNKNOWN")
            block += _str_triple(uri, "ind:cnName", r.get("cn_name") or r.get("column_comment"))
            block += _str_triple(uri, "ind:columnComment", r.get("column_comment") or r.get("cn_name"))
            block += _bool_triple(uri, "ind:isPrimaryKey", r.get("is_primary_key"))
            block += _bool_triple(uri, "ind:isNullable", r.get("is_nullable"))
            block += _int_triple(uri, "ind:ordinalPosition", r.get("ordinal_position"))
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)
            out.append(f"tbl:{table_id} ind:hasColumn {uri} .\n")
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # dimension
    # ------------------------------------------------------------------ #

    def _export_dimensions(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "dimension"):
            return ""
        rows = self._fetchall(cur, """
            SELECT id, en_name, code, cn_name, caption, view_type, dim_type,
                   defintion, description, leaf_category_id, online,
                   is_delete, is_hyper
            FROM dimension
            WHERE COALESCE(is_delete, 0) = 0
            ORDER BY id
        """)
        if not rows:
            return ""
        self._log(f"  dimension: {len(rows)} 条")

        # Load level mappings: dim_id → level_uri (use row id for uniqueness)
        level_map: dict[int, str] = {}
        if self._table_exists(tables, "level"):
            for r in self._fetchall(cur,
                    "SELECT id, dim_id FROM level WHERE dim_id IS NOT NULL"):
                if r["dim_id"]:
                    # Store first level per dim_id (lowest id wins)
                    dim_key = int(r["dim_id"])
                    if dim_key not in level_map:
                        level_map[dim_key] = f"hier:level_{r['id']}"

        out = [f"# ── 维度 ({len(rows)}) ──\n"]
        for r in rows:
            uri = f"dim:{r['id']}"
            block = f"{uri} a ind:Dimension ;\n"
            block += _int_triple(uri, "ind:localId", r["id"])
            block += _str_triple(uri, "ind:code", r.get("code"))
            block += _str_triple(uri, "ind:cnName", r.get("cn_name"))
            block += _str_triple(uri, "ind:caption", r.get("caption"))
            block += _str_triple(uri, "ind:enName", r.get("en_name"))
            block += _str_triple(uri, "ind:definition",
                                 r.get("defintion") or r.get("definition"))
            block += _str_triple(uri, "ind:description", r.get("description"))
            block += _int_triple(uri, "ind:dimTypeCode", r.get("dim_type"))
            block += _int_triple(uri, "ind:viewType", r.get("view_type"))
            block += _int_triple(uri, "ind:isHyper", r.get("is_hyper", 0))
            block += _int_triple(uri, "ind:online", r.get("online", 1))
            cat_id = r.get("leaf_category_id")
            if cat_id:
                block += _ref_triple("ind:inCategory", f"cat:{cat_id}")
            lvl_uri = level_map.get(int(r["id"]))
            if lvl_uri:
                block += _ref_triple("ind:hasLevel", lvl_uri)
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # measure
    # Note: `meas_type` column does NOT exist; derived later from applications.
    #       `function_type` is NULL in most rows; filled later from expression JSON.
    # ------------------------------------------------------------------ #

    def _export_measures(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "measure"):
            return ""
        rows = self._fetchall(cur, """
            SELECT id, en_name, code, cn_name, online, unit, caliber,
                   definition, description, leaf_category_id,
                   north_star, is_delete, owner_user, develop_user, function_type
            FROM measure
            WHERE COALESCE(is_delete, 0) = 0
            ORDER BY id
        """)
        if not rows:
            return ""
        self._log(f"  measure: {len(rows)} 条")
        out = [f"# ── 指标 ({len(rows)}) ──\n"]
        for r in rows:
            uri = f"meas:{r['id']}"
            block = f"{uri} a ind:Measure ;\n"
            block += _int_triple(uri, "ind:localId", r["id"])
            block += _str_triple(uri, "ind:code", r.get("code"))
            block += _str_triple(uri, "ind:cnName", r.get("cn_name"))
            block += _str_triple(uri, "ind:enName", r.get("en_name"))
            block += _str_triple(uri, "ind:unit", r.get("unit"))
            block += _str_triple(uri, "ind:caliber", r.get("caliber"))
            block += _str_triple(uri, "ind:definition", r.get("definition"))
            block += _str_triple(uri, "ind:description", r.get("description"))
            block += _int_triple(uri, "ind:online", r.get("online", 1))
            block += _int_triple(uri, "ind:northStar", r.get("north_star", 0))
            block += _str_triple(uri, "ind:ownerUser", r.get("owner_user"))
            block += _str_triple(uri, "ind:developUser", r.get("develop_user"))
            # function_type from DB may be NULL; will be patched by _derive_measure_metadata
            block += _str_triple(uri, "ind:functionType", r.get("function_type"))
            cat_id = r.get("leaf_category_id")
            if cat_id:
                block += _ref_triple("ind:inCategory", f"cat:{cat_id}")
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # measure_application
    # Fix: no `is_del` column; use `available` only.
    # Enhancement: extract aggFunction from expression JSON.
    # Returns: (turtle_str, {meas_id → agg_func_str})
    # ------------------------------------------------------------------ #

    def _export_measure_applications(
        self, cur, tables: set
    ) -> tuple[str, dict[int, str]]:
        if not self._table_exists(tables, "measure_application"):
            return "", {}
        rows = self._fetchall(cur, """
            SELECT id, meas_id, apply_type, expression, dw_table_id,
                   fact_column, where_condition, available, data_type
            FROM measure_application
            WHERE COALESCE(available, 1) = 1
            ORDER BY id
        """)
        if not rows:
            return "", {}
        self._log(f"  measure_application: {len(rows)} 条")

        # meas_id → first detected agg function (for later patching on Measure)
        mapp_agg_map: dict[int, str] = {}

        out = [f"# ── 指标应用 ({len(rows)}) ──\n"]
        for r in rows:
            uri = f"mapp:{r['id']}"

            # Extract aggregation function from expression JSON
            agg_func = _extract_agg_func(r.get("expression"))

            block = f"{uri} a ind:MeasureApplication ;\n"
            block += _int_triple(uri, "ind:localId", r["id"])
            block += _int_triple(uri, "ind:applyType", r.get("apply_type"))
            block += _str_triple(uri, "ind:factColumn", r.get("fact_column"))
            block += _str_triple(uri, "ind:aggFunction", agg_func)
            block += _str_triple(uri, "ind:expression", r.get("expression"))
            block += _str_triple(uri, "ind:whereCondition", r.get("where_condition"))
            block += _str_triple(uri, "ind:dataType", r.get("data_type"))
            block += _int_triple(uri, "ind:available", r.get("available", 1))
            tbl_id = r.get("dw_table_id")
            if tbl_id:
                block += _ref_triple("ind:onFactTable", f"tbl:{tbl_id}")
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)

            # Link measure → application
            meas_id = r.get("meas_id")
            if meas_id:
                out.append(f"meas:{meas_id} ind:hasApplication {uri} .\n")
                # Record first agg func for this measure
                if agg_func and int(meas_id) not in mapp_agg_map:
                    mapp_agg_map[int(meas_id)] = agg_func

        return "\n".join(out), mapp_agg_map

    # ------------------------------------------------------------------ #
    # dimension_application
    # ------------------------------------------------------------------ #

    def _export_dimension_applications(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "dimension_application"):
            return ""
        # Try to include dim_column_expr if the column exists
        has_dim_column_expr = False
        try:
            cur.execute("SELECT dim_column_expr FROM dimension_application LIMIT 0")
            has_dim_column_expr = True
        except Exception:
            pass

        col_list = "id, dim_id, dw_table_id, source_type, fact_column, available, data_type"
        if has_dim_column_expr:
            col_list += ", dim_column_expr"

        rows = self._fetchall(cur, f"""
            SELECT {col_list}
            FROM dimension_application
            WHERE COALESCE(available, 1) = 1
            ORDER BY id
        """)
        if not rows:
            return ""
        self._log(f"  dimension_application: {len(rows)} 条")
        out = [f"# ── 维度应用 ({len(rows)}) ──\n"]
        for r in rows:
            uri = f"dapp:{r['id']}"
            block = f"{uri} a ind:DimensionApp ;\n"
            block += _int_triple(uri, "ind:localId", r["id"])
            block += _str_triple(uri, "ind:dimFactColumn", r.get("fact_column"))
            block += _int_triple(uri, "ind:sourceType", r.get("source_type"))
            block += _str_triple(uri, "ind:dataType", r.get("data_type"))
            block += _int_triple(uri, "ind:available", r.get("available", 1))
            tbl_id = r.get("dw_table_id")
            if tbl_id:
                block += _ref_triple("ind:dimFactTable", f"tbl:{tbl_id}")
            # dimColumnExpr is required for attribute dimensions that share the
            # same FK/PK but expose different value columns from one dim table.
            dim_col_expr = r.get("dim_column_expr")
            if not dim_col_expr and r.get("dim_type") == 2:
                dim_col = (r.get("dim_column") or "").strip()
                dim_pk = (r.get("dim_primary_key") or "").strip()
                if dim_col and dim_col != dim_pk:
                    dim_col_expr = f"{{d}}.{dim_col}"
            if dim_col_expr:
                block += _str_triple(uri, "ind:dimColumnExpr", dim_col_expr)
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)
            dim_id = r.get("dim_id")
            if dim_id:
                out.append(f"dim:{dim_id} ind:hasDimApp {uri} .\n")
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # dimension_dimtable_connect
    # Note: no dw_table_id in this table; uses schema_name + dim_table_name
    # ------------------------------------------------------------------ #

    def _export_dimtable_connects(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "dimension_dimtable_connect"):
            return ""
        rows = self._fetchall(cur, """
            SELECT id, dim_id, schema_name, dim_table_name,
                   dim_primary_key, dim_value_column, where_condition
            FROM dimension_dimtable_connect
            ORDER BY id
        """)
        if not rows:
            return ""
        self._log(f"  dimension_dimtable_connect: {len(rows)} 条")
        out = [f"# ── 维度-维表关联 ({len(rows)}) ──\n"]
        for r in rows:
            dim_id = r.get("dim_id") or r["id"]
            uri = f"dconn:{dim_id}"
            block = f"{uri} a ind:DimensionDimtableConnect ;\n"
            block += _int_triple(uri, "ind:localId", r["id"])
            block += _str_triple(uri, "ind:schemaName", r.get("schema_name"))
            # Store full dim table path: schema.table
            full_table = ""
            if r.get("schema_name") and r.get("dim_table_name"):
                full_table = f"{r['schema_name']}.{r['dim_table_name']}"
            elif r.get("dim_table_name"):
                full_table = r["dim_table_name"]
            block += _str_triple(uri, "ind:tableName", full_table)
            block += _str_triple(uri, "ind:dimPrimaryKey", r.get("dim_primary_key"))
            block += _str_triple(uri, "ind:dimValueColumn", r.get("dim_value_column"))
            block += _str_triple(uri, "ind:whereCondition", r.get("where_condition"))
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)
            if dim_id:
                out.append(f"dim:{dim_id} ind:hasDimtableConnect {uri} .\n")
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # NaturalDimMapping（新结构，替代旧 NaturalDateMapping）
    # 为每个 MeasureApp 生成至少一个日期公共维度映射。
    # 策略 1：measure_natural_date_mapping + dimension_application 精确匹配
    # 策略 2：dimension_application 中按列名启发式查找日期列
    # ------------------------------------------------------------------ #

    _DATE_COL_PRIORITY = (
        "updatetime", "update_time", "createtime", "create_time", "created_at",
        "dt", "date", "order_date", "operate_date", "operate_time",
    )

    def _build_natural_dim_mappings(self, cur, tables: set) -> str:
        """Generate ind:NaturalDimMapping instances for each MeasureApp."""
        if not self._table_exists(tables, "measure_application"):
            return ""

        # mapp_id → physical_column
        mapp_date_col: dict[int, str] = {}

        # Strategy 1: measure_natural_date_mapping + dimension_application
        if (self._table_exists(tables, "measure_natural_date_mapping") and
                self._table_exists(tables, "dimension_application")):
            try:
                rows = self._fetchall(cur, """
                    SELECT ma.id AS mapp_id, da.fact_column AS physical_column
                    FROM measure_application ma
                    JOIN measure_natural_date_mapping ndm
                         ON ndm.meas_id = ma.meas_id
                        AND ndm.dw_table_id = ma.dw_table_id
                    JOIN dimension_application da
                         ON da.dim_id = ndm.target_dim_id
                        AND da.dw_table_id = ma.dw_table_id
                    WHERE COALESCE(ma.available, 1) = 1
                      AND da.fact_column IS NOT NULL AND da.fact_column != ''
                    ORDER BY ma.id, da.id
                """)
                for r in rows:
                    mid = int(r["mapp_id"])
                    if mid not in mapp_date_col:
                        mapp_date_col[mid] = r["physical_column"]
            except Exception:
                pass

        # Strategy 2: heuristic — find date-like column from dimension_application
        if self._table_exists(tables, "dimension_application"):
            try:
                missing_rows = self._fetchall(cur, """
                    SELECT id AS mapp_id, dw_table_id
                    FROM measure_application
                    WHERE COALESCE(available, 1) = 1
                    ORDER BY id
                """)
                missing: dict[int, int] = {
                    int(r["mapp_id"]): r["dw_table_id"]
                    for r in missing_rows
                    if int(r["mapp_id"]) not in mapp_date_col
                       and r.get("dw_table_id")
                }
                if missing:
                    tbl_ids = list(set(missing.values()))
                    placeholders = ",".join(["%s"] * len(tbl_ids))
                    da_rows = self._fetchall(cur, f"""
                        SELECT dw_table_id, fact_column
                        FROM dimension_application
                        WHERE dw_table_id IN ({placeholders})
                          AND COALESCE(available, 1) = 1
                          AND fact_column IS NOT NULL AND fact_column != ''
                        ORDER BY dw_table_id, id
                    """, tbl_ids)
                    # Group columns by table
                    tbl_cols: dict[int, list[str]] = {}
                    for r in da_rows:
                        tbl_cols.setdefault(r["dw_table_id"], []).append(r["fact_column"])
                    # Pick best date column per table by priority
                    tbl_date: dict[int, str] = {}
                    for tid, cols in tbl_cols.items():
                        cols_lower = {c.lower(): c for c in cols}
                        for prio in self._DATE_COL_PRIORITY:
                            if prio in cols_lower:
                                tbl_date[tid] = cols_lower[prio]
                                break
                        else:
                            # fallback: any column containing time/date/dt
                            for c in cols:
                                if any(k in c.lower() for k in ("time", "date", "dt")):
                                    tbl_date[tid] = c
                                    break
                    for mapp_id, tbl_id in missing.items():
                        if tbl_id in tbl_date:
                            mapp_date_col[mapp_id] = tbl_date[tbl_id]
            except Exception:
                pass

        if not mapp_date_col:
            return ""

        self._log(f"  NaturalDimMapping(date): {len(mapp_date_col)} 条")
        out = [f"# ── 公共维度映射 ({len(mapp_date_col)}) ──\n"]
        for mapp_id, phys_col in sorted(mapp_date_col.items()):
            ndm_uri = f"inst:ndm_{mapp_id}_date"
            mapp_uri = f"mapp:{mapp_id}"
            block  = f"{ndm_uri} a ind:NaturalDimMapping ;\n"
            block += f'    ind:naturalHierarchyCode "h_date" ;\n'
            block += f'    ind:physicalColumn       "{_esc(phys_col)}" .\n'
            out.append(block)
            out.append(f"{mapp_uri} ind:hasNaturalDimMapping {ndm_uri} .\n")
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # measure_natural_date_mapping（旧结构，保留备用）
    # Fix: columns are natural_dim_id / target_dim_id (not dim_id / natural_date_dim_id)
    # ------------------------------------------------------------------ #

    def _export_natural_date_mappings(self, cur, tables: set) -> str:
        if not self._table_exists(tables, "measure_natural_date_mapping"):
            return ""
        rows = self._fetchall(cur, """
            SELECT id, meas_id, natural_dim_id, target_dim_id, dw_table_id
            FROM measure_natural_date_mapping
            ORDER BY id
        """)
        if not rows:
            return ""
        self._log(f"  measure_natural_date_mapping: {len(rows)} 条")
        out = [f"# ── 自然日期映射 ({len(rows)}) ──\n"]
        for r in rows:
            uri = f"ndmap:{r['id']}"
            block = f"{uri} a ind:NaturalDateMapping ;\n"
            block += _int_triple(uri, "ind:localId", r["id"])
            nat_id = r.get("natural_dim_id")
            tgt_id = r.get("target_dim_id")
            tbl_id = r.get("dw_table_id")
            if nat_id:
                block += _ref_triple("ind:mappingNaturalDim", f"dim:{nat_id}")
            if tgt_id:
                block += _ref_triple("ind:mappingTargetDim", f"dim:{tgt_id}")
            if tbl_id:
                block += _ref_triple("ind:mappingOnTable", f"tbl:{tbl_id}")
            block = block.rstrip(" ;\n") + " .\n"
            out.append(block)
            meas_id = r.get("meas_id")
            if meas_id:
                out.append(f"meas:{meas_id} ind:hasNaturalDateMapping {uri} .\n")
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # measure → sourceTable (direct, derived from measure_application)
    # Fix: no is_del column
    # ------------------------------------------------------------------ #

    def _export_measure_source_tables(self, cur, tables: set) -> str:
        """Generate ind:sourceTable direct links from each measure to its fact tables."""
        if not self._table_exists(tables, "measure_application"):
            return ""
        rows = self._fetchall(cur, """
            SELECT DISTINCT meas_id, dw_table_id
            FROM measure_application
            WHERE COALESCE(available, 1) = 1
              AND meas_id IS NOT NULL
              AND dw_table_id IS NOT NULL
            ORDER BY meas_id, dw_table_id
        """)
        if not rows:
            return ""
        self._log(f"  measure→sourceTable: {len(rows)} 条")
        out = [f"# ── 指标来源表（直连）({len(rows)}) ──\n"]
        for r in rows:
            out.append(f"meas:{r['meas_id']} ind:sourceTable tbl:{r['dw_table_id']} .\n")
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # measure → availableDimension (direct, derived from shared fact table)
    # Fix: no is_del column
    # ------------------------------------------------------------------ #

    def _export_measure_available_dimensions(self, cur, tables: set) -> str:
        """
        ind:availableDimension: measures that share a fact table with a dimension
        (via measure_application.dw_table_id == dimension_application.dw_table_id).
        """
        if not (self._table_exists(tables, "measure_application") and
                self._table_exists(tables, "dimension_application")):
            return ""
        rows = self._fetchall(cur, """
            SELECT DISTINCT ma.meas_id, da.dim_id
            FROM measure_application ma
            JOIN dimension_application da ON da.dw_table_id = ma.dw_table_id
            WHERE COALESCE(ma.available, 1) = 1
              AND COALESCE(da.available, 1) = 1
              AND ma.meas_id IS NOT NULL
              AND da.dim_id IS NOT NULL
            ORDER BY ma.meas_id, da.dim_id
        """)
        if not rows:
            return ""
        self._log(f"  measure→availableDimension: {len(rows)} 条")
        out = [f"# ── 指标可用维度（直连）({len(rows)}) ──\n"]
        for r in rows:
            out.append(f"meas:{r['meas_id']} ind:availableDimension dim:{r['dim_id']} .\n")
        return "\n".join(out)

    # ------------------------------------------------------------------ #
    # Derive measType + functionType on Measure from application data
    # measType  → from measure_application.apply_type (0=原子,1=衍生,2=派生)
    # functionType → from expression JSON's primary operator
    # ------------------------------------------------------------------ #

    def _derive_measure_metadata(
        self,
        cur,
        tables: set,
        mapp_agg_map: dict[int, str],
    ) -> str:
        """
        Add ind:measType and ind:functionType supplements to existing Measure nodes.
        Only emitted for measures where the DB has application data.
        """
        if not self._table_exists(tables, "measure_application"):
            return ""

        # One row per measure: take the first application's apply_type + expression
        rows = self._fetchall(cur, """
            SELECT meas_id,
                   MIN(apply_type)  AS apply_type,
                   MIN(expression)  AS expression
            FROM measure_application
            WHERE COALESCE(available, 1) = 1
              AND meas_id IS NOT NULL
            GROUP BY meas_id
            ORDER BY meas_id
        """)
        if not rows:
            return ""

        out = ["# ── 指标类型 & 聚合函数（从应用推导）──\n"]
        for r in rows:
            meas_id   = r["meas_id"]
            apply_type = r.get("apply_type")
            # Map apply_type → measType (same semantics)
            meas_type = apply_type  # 0=原子, 1=衍生, 2=派生

            # Aggregation function: prefer from pre-computed map, else re-extract
            agg_func = mapp_agg_map.get(int(meas_id), "") or \
                       _extract_agg_func(r.get("expression"))

            lines = []
            if meas_type is not None:
                lines.append(f"    ind:measType {int(meas_type)} ;")
            if agg_func:
                lines.append(f'    ind:functionType "{_esc(agg_func)}" ;')

            if lines:
                body = "\n".join(lines)
                # Close with '.' replacing the last ';'
                body = body.rstrip().rstrip(";").rstrip() + " ."
                out.append(f"meas:{meas_id}\n{body}\n")

        return "\n".join(out)
